package ilpak.nomat.room.out

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import ilpak.nomat.room.application.domain.PassOutcome
import ilpak.nomat.room.application.domain.RoundPhase
import ilpak.nomat.room.application.domain.RoundSnapshot
import ilpak.nomat.room.application.domain.RoundStateStore
import ilpak.nomat.room.application.domain.RoundTrackSpec
import ilpak.nomat.room.application.domain.RoundTransition
import ilpak.nomat.room.application.domain.ScoreEntry
import ilpak.nomat.room.application.domain.TransitionResult
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Repository

/**
 * 라운드 상태 어댑터 — `StringRedisTemplate` + Lua 스크립트로 라운드 전이를 원자 조작한다
 * (`ActiveSessionManager`의 Lua CAS 패턴 확장). 모든 전이 게이트는 `(roundSeq, phase)` CAS,
 * 모든 시각 앵커·비교는 `redis.call('TIME')` 단일 시계, phase HSET과 deadline ZADD/ZREM은 동일 스크립트다.
 *
 * 키 스킴과 slot 배치는 [RoundRedisKeys] 참조. 한 방의 round Hash·scores ZSET·passes SET·마감 인덱스 샤드를
 * 동일 hash tag `{shard}`로 묶어 같은 slot에 두므로, 멀티 키 원자 Lua가 **클러스터 모드에서도**
 * `CROSSSLOT` 없이 동작한다. 방은 샤드를 넘나들지 않아 per-shard로 단일 시계가 성립한다.
 * - round(Hash): roundSeq·phase·deadlineAt·trackIndex·winnerId·totalRounds·trackOrder·trackDurations·passSeq
 * - deadlines(샤드 ZSET): score=deadlineAt(ms), member=roomId
 * - scores(ZSET): member=playerId, score=누적 점수
 * - passes(SET): member=포기 중인 playerId
 *
 * **포기 집합의 유효성 계약** — `passes`는 `passSeq == roundSeq`일 때만 유효하다. 라운드 경계에서는 키를
 * 비우지 않고(lazy reset) 다음 진입 시점에 판별해 폐기하므로, 매 라운드 전이가 지나가는
 * [ADVANCE_ON_DEADLINE_SCRIPT]·[ADVANCE_ON_CORRECT_SCRIPT]를 **한 줄도 건드리지 않는다.**
 * 이 판별식이 성립하지 않는 유일한 지점이 게임 경계(`roundSeq`가 1로 되돌아감)라, 거기서만
 * [START_SCRIPT]가 명시적으로 지운다.
 */
@Repository
private class RoundStateStoreImpl(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) : RoundStateStore {

    override fun start(roomId: Long, tracks: List<RoundTrackSpec>, playerIds: Set<Long>): RoundTransition {
        val raw = redisTemplate.execute(
            START_SCRIPT,
            listOf(
                RoundRedisKeys.round(roomId),
                RoundRedisKeys.deadlines(roomId),
                RoundRedisKeys.scores(roomId),
                RoundRedisKeys.passes(roomId),
            ),
            roomId.toString(),
            objectMapper.writeValueAsString(tracks.map { it.trackId }),
            objectMapper.writeValueAsString(tracks.map { it.openDurationMillis }),
            objectMapper.writeValueAsString(playerIds.map { it.toString() }),
            tracks.size.toString(),
            TTL_SECONDS.toString(),
        )
        return parse(raw)
    }

    override fun tryAdvanceOnDeadline(roomId: Long, expectedSeq: Long): RoundTransition {
        val raw = redisTemplate.execute(
            ADVANCE_ON_DEADLINE_SCRIPT,
            listOf(RoundRedisKeys.round(roomId), RoundRedisKeys.deadlines(roomId)),
            roomId.toString(),
            expectedSeq.toString(),
            REVEAL_MILLIS.toString(),
            TTL_SECONDS.toString(),
        )
        return parse(raw)
    }

    override fun tryAdvanceOnCorrect(roomId: Long, expectedSeq: Long, winnerId: Long): RoundTransition {
        val raw = redisTemplate.execute(
            ADVANCE_ON_CORRECT_SCRIPT,
            listOf(RoundRedisKeys.round(roomId), RoundRedisKeys.deadlines(roomId), RoundRedisKeys.scores(roomId)),
            roomId.toString(),
            expectedSeq.toString(),
            winnerId.toString(),
            REVEAL_MILLIS.toString(),
            TTL_SECONDS.toString(),
        )
        return parse(raw)
    }

    override fun togglePass(roomId: Long, expectedSeq: Long, playerId: Long): PassOutcome {
        val raw = redisTemplate.execute(
            TOGGLE_PASS_SCRIPT,
            passKeys(roomId),
            roomId.toString(),
            expectedSeq.toString(),
            playerId.toString(),
            REVEAL_MILLIS.toString(),
            TTL_SECONDS.toString(),
            PASS_NUMERATOR.toString(),
            PASS_DENOMINATOR.toString(),
        )
        return parsePass(raw)
    }

    override fun onPlayerLeft(roomId: Long, playerId: Long): PassOutcome {
        val raw = redisTemplate.execute(
            ON_PLAYER_LEFT_SCRIPT,
            passKeys(roomId),
            roomId.toString(),
            playerId.toString(),
            REVEAL_MILLIS.toString(),
            TTL_SECONDS.toString(),
            PASS_NUMERATOR.toString(),
            PASS_DENOMINATOR.toString(),
        )
        return parsePass(raw)
    }

    override fun isPassing(roomId: Long, playerId: Long): Boolean {
        val raw = redisTemplate.execute(
            IS_PASSING_SCRIPT,
            listOf(RoundRedisKeys.round(roomId), RoundRedisKeys.passes(roomId)),
            playerId.toString(),
        )
        return raw == 1L
    }

    override fun snapshot(roomId: Long, viewerId: Long?): RoundSnapshot? {
        val hash = redisTemplate.opsForHash<String, String>().entries(RoundRedisKeys.round(roomId))
        if (hash.isEmpty()) {
            return null
        }
        val trackOrder = objectMapper.readValue<List<Long>>(hash.getValue("trackOrder"))
        val trackIndex = hash.getValue("trackIndex").toInt()
        val scores = scoreboard(roomId)
        // 포기 집합은 `passSeq == roundSeq`일 때만 유효하다 — 불일치면 이전 라운드의 잔재이므로 0명으로 본다.
        val passesValid = hash["passSeq"] != null && hash["passSeq"] == hash.getValue("roundSeq")
        val passesKey = RoundRedisKeys.passes(roomId)
        val passedCount = if (passesValid) {
            redisTemplate.opsForSet().size(passesKey)?.toInt() ?: 0
        } else {
            0
        }
        val passing = passesValid && viewerId != null &&
            redisTemplate.opsForSet().isMember(passesKey, viewerId.toString()) == true
        return RoundSnapshot(
            phase = RoundPhase.valueOf(hash.getValue("phase")),
            roundSeq = hash.getValue("roundSeq").toLong(),
            totalRounds = hash.getValue("totalRounds").toInt(),
            trackIndex = trackIndex,
            trackOrder = trackOrder,
            currentTrackId = trackOrder[trackIndex],
            deadlineAt = hash.getValue("deadlineAt").toLong(),
            winnerId = hash["winnerId"]?.takeIf { it.isNotEmpty() }?.toLong(),
            scores = scores,
            passedCount = passedCount,
            requiredCount = requiredCount(scores.size),
            passing = passing,
        )
    }

    override fun findDueRoomIds(): List<Long> {
        // 마감 인덱스가 SHARD_COUNT개 샤드로 흩어져 있으므로(클러스터 slot 분산) 전 샤드를 순회해 합친다.
        // 각 FIND_DUE는 단일 키라 CROSSSLOT 대상이 아니며, 빈 샤드 ZSET 조회는 빈 결과로 즉시 반환된다.
        val dueRoomIds = mutableListOf<Long>()
        for (shard in 0 until RoundRedisKeys.SHARD_COUNT) {
            val members = redisTemplate.execute(FIND_DUE_SCRIPT, listOf(RoundRedisKeys.deadlinesShard(shard)))
                ?: continue
            members.forEach { dueRoomIds.add(it.toString().toLong()) }
        }
        return dueRoomIds
    }

    override fun scoreboard(roomId: Long): List<ScoreEntry> {
        val entries = redisTemplate.opsForZSet().reverseRangeWithScores(RoundRedisKeys.scores(roomId), 0, -1)
            ?: return emptyList()
        return entries.mapNotNull { tuple ->
            val playerId = tuple.value?.toLong() ?: return@mapNotNull null
            ScoreEntry(playerId, tuple.score?.toInt() ?: 0)
        }
    }

    override fun teardown(roomId: Long) {
        redisTemplate.execute(
            TEARDOWN_SCRIPT,
            listOf(
                RoundRedisKeys.round(roomId),
                RoundRedisKeys.deadlines(roomId),
                RoundRedisKeys.scores(roomId),
                RoundRedisKeys.passes(roomId),
            ),
            roomId.toString(),
        )
    }

    companion object {
        /** 포기 경로 두 스크립트가 공유하는 KEYS 배치(round·deadlines·scores·passes 순). */
        private fun passKeys(roomId: Long) = listOf(
            RoundRedisKeys.round(roomId),
            RoundRedisKeys.deadlines(roomId),
            RoundRedisKeys.scores(roomId),
            RoundRedisKeys.passes(roomId),
        )

        private fun parse(raw: String?): RoundTransition {
            if (raw == null || raw == IGNORED_CODE) {
                return RoundTransition(TransitionResult.IGNORED)
            }
            if (raw == NOT_DUE_CODE) {
                return RoundTransition(TransitionResult.NOT_DUE)
            }
            val parts = raw.split('|')
            return RoundTransition(
                result = TransitionResult.TRANSITIONED,
                phase = RoundPhase.valueOf(parts[1]),
                roundSeq = parts[2].toLong(),
                trackIndex = parts[3].toInt(),
                deadlineAt = parts[4].toLong(),
                winnerId = parts.getOrNull(5)?.takeIf { it.isNotEmpty() }?.toLong(),
            )
        }

        /**
         * 포기 경로 응답 해석. 전이는 기존 `"1|…"` 포맷을 그대로 쓰고, 진행만 갱신된 경우는
         * `"2|<passed>|<required>|<passing>|<roundSeq>"`로 온다. `"0"`·`"-1"`은 기존과 동일한 no-op 코드다.
         *
         * 진행 응답이 `roundSeq`를 함께 싣는 이유: 퇴장 재평가는 호출 지점이 라운드를 모르므로, 브로드캐스트가
         * 실을 `roundSeq`를 스크립트가 읽은 값으로 돌려받아야 한다(별도 왕복으로 읽으면 그 사이 전이될 수 있다).
         */
        private fun parsePass(raw: String?): PassOutcome {
            if (raw == null || !raw.startsWith(PASS_PROGRESS_PREFIX)) {
                return PassOutcome(parse(raw))
            }
            val parts = raw.split('|')
            return PassOutcome(
                transition = RoundTransition(TransitionResult.IGNORED),
                roundSeq = parts[4].toLong(),
                passedCount = parts[1].toInt(),
                requiredCount = parts[2].toInt(),
                passing = parts[3] == "1",
            )
        }

        /** 남은 인원 [remaining]에 대한 필요 포기 인원 = `ceil(remaining * NUMERATOR / DENOMINATOR)`. */
        private fun requiredCount(remaining: Int): Int =
            (remaining * PASS_NUMERATOR + PASS_DENOMINATOR - 1) / PASS_DENOMINATOR

        private const val REVEAL_MILLIS = 10_000L
        private const val TTL_SECONDS = 86_400L
        private const val IGNORED_CODE = "0"
        private const val NOT_DUE_CODE = "-1"
        private const val PASS_PROGRESS_PREFIX = "2|"

        /**
         * 포기 임계 = 남은 인원의 `PASS_NUMERATOR / PASS_DENOMINATOR` 이상.
         *
         * 나눗셈·반올림 없이 `passed * DENOMINATOR >= n * NUMERATOR` 정수 비교로 판정하므로 경계 논쟁이 없다.
         * 배포 후 조기 종료가 과하면 3/4로 올리는 것이 첫 조정 수단이고, 그때 바뀌는 것은 이 두 상수뿐이다.
         */
        private const val PASS_NUMERATOR = 2
        private const val PASS_DENOMINATOR = 3

        // Redis TIME([초, 마이크로초])을 ms epoch로 환산하는 공통 조각.
        private const val NOW_MS =
            "local t = redis.call('TIME'); local now = (tonumber(t[1]) * 1000) + math.floor(tonumber(t[2]) / 1000)"

        // 포기 집합 lazy reset 조각 — `passSeq != roundSeq`면 이전 라운드의 잔재이므로 지연 폐기한다.
        // KEYS[1]=round Hash, KEYS[4]=passes SET, 지역 변수 seq를 전제로 한다.
        private const val RESET_STALE_PASSES = """
            local passSeq = redis.call('HGET', KEYS[1], 'passSeq')
            if passSeq ~= tostring(seq) then
                redis.call('DEL', KEYS[4])
                redis.call('HSET', KEYS[1], 'passSeq', seq)
            end
        """

        // 임계 도달 시 OPEN→REVEAL 전이 조각 — 세 종료 경로가 공유하는 것과 동일한 (roundSeq, phase) CAS다.
        private const val REVEAL_ON_PASS = """
            local newSeq = seq + 1
            local revealDeadline = now + revealMillis
            redis.call('HSET', KEYS[1], 'roundSeq', newSeq, 'phase', 'REVEAL',
                'deadlineAt', revealDeadline, 'winnerId', '')
            redis.call('EXPIRE', KEYS[1], ttl)
            redis.call('ZADD', KEYS[2], revealDeadline, ARGV[1])
            return '1|REVEAL|' .. newSeq .. '|' .. trackIndex .. '|' .. revealDeadline .. '|'
        """

        // 결과 포맷: "1|<phase>|<seq>|<trackIndex>|<deadlineAt>|<winnerId>" / no-op은 "0"·"-1".
        //
        // 게임 경계 정리(Decision 5-1): `DEL passes` + `HDEL passSeq`는 라운드 Hash 초기화와 같은 원자
        // 실행 단위에 있어야 한다. 게임이 자연 종료(`ENDED`)되면 teardown이 호출되지 않아 포기 집합이
        // 24h TTL로 살아남는데, 재시작하면 `roundSeq`가 1로 되돌아가 lazy reset의 `passSeq != roundSeq`
        // 판별식이 이월된 잔재를 "유효"로 읽는다(이전 게임 1라운드의 passSeq=1 == 새 게임 roundSeq=1).
        // 그러면 그 참가자는 새 곡을 듣기도 전에 정답 판정에서 제외되고 임계가 0이 아닌 값에서 시작한다.
        private val START_SCRIPT = DefaultRedisScript(
            """
            local durations = cjson.decode(ARGV[3])
            local players = cjson.decode(ARGV[4])
            local total = tonumber(ARGV[5])
            local ttl = tonumber(ARGV[6])
            $NOW_MS
            local deadline = now + tonumber(durations[1])
            redis.call('HSET', KEYS[1],
                'roundSeq', 1, 'phase', 'OPEN', 'deadlineAt', deadline,
                'trackIndex', 0, 'winnerId', '', 'totalRounds', total,
                'trackOrder', ARGV[2], 'trackDurations', ARGV[3])
            redis.call('HDEL', KEYS[1], 'passSeq')
            redis.call('DEL', KEYS[4])
            redis.call('EXPIRE', KEYS[1], ttl)
            redis.call('ZADD', KEYS[2], deadline, ARGV[1])
            for i = 1, #players do
                redis.call('ZADD', KEYS[3], 0, players[i])
            end
            if #players > 0 then
                redis.call('EXPIRE', KEYS[3], ttl)
            end
            return '1|OPEN|1|0|' .. deadline .. '|'
            """.trimIndent(),
            String::class.java,
        )

        private val ADVANCE_ON_DEADLINE_SCRIPT = DefaultRedisScript(
            """
            if redis.call('EXISTS', KEYS[1]) == 0 then return '0' end
            local seq = tonumber(redis.call('HGET', KEYS[1], 'roundSeq'))
            if seq ~= tonumber(ARGV[2]) then return '0' end
            local phase = redis.call('HGET', KEYS[1], 'phase')
            local deadline = tonumber(redis.call('HGET', KEYS[1], 'deadlineAt'))
            local trackIndex = tonumber(redis.call('HGET', KEYS[1], 'trackIndex'))
            local total = tonumber(redis.call('HGET', KEYS[1], 'totalRounds'))
            local ttl = tonumber(ARGV[4])
            $NOW_MS
            if now < deadline then return '-1' end
            local newSeq = seq + 1
            if phase == 'OPEN' then
                local revealDeadline = now + tonumber(ARGV[3])
                redis.call('HSET', KEYS[1], 'roundSeq', newSeq, 'phase', 'REVEAL',
                    'deadlineAt', revealDeadline, 'winnerId', '')
                redis.call('EXPIRE', KEYS[1], ttl)
                redis.call('ZADD', KEYS[2], revealDeadline, ARGV[1])
                return '1|REVEAL|' .. newSeq .. '|' .. trackIndex .. '|' .. revealDeadline .. '|'
            elseif phase == 'REVEAL' then
                local nextIndex = trackIndex + 1
                if nextIndex >= total then
                    redis.call('HSET', KEYS[1], 'roundSeq', newSeq, 'phase', 'ENDED')
                    redis.call('EXPIRE', KEYS[1], ttl)
                    redis.call('ZREM', KEYS[2], ARGV[1])
                    return '1|ENDED|' .. newSeq .. '|' .. trackIndex .. '|0|'
                else
                    local durations = cjson.decode(redis.call('HGET', KEYS[1], 'trackDurations'))
                    local nextDeadline = now + tonumber(durations[nextIndex + 1])
                    redis.call('HSET', KEYS[1], 'roundSeq', newSeq, 'phase', 'OPEN',
                        'trackIndex', nextIndex, 'deadlineAt', nextDeadline, 'winnerId', '')
                    redis.call('EXPIRE', KEYS[1], ttl)
                    redis.call('ZADD', KEYS[2], nextDeadline, ARGV[1])
                    return '1|OPEN|' .. newSeq .. '|' .. nextIndex .. '|' .. nextDeadline .. '|'
                end
            else
                return '0'
            end
            """.trimIndent(),
            String::class.java,
        )

        private val ADVANCE_ON_CORRECT_SCRIPT = DefaultRedisScript(
            """
            if redis.call('EXISTS', KEYS[1]) == 0 then return '0' end
            local seq = tonumber(redis.call('HGET', KEYS[1], 'roundSeq'))
            if seq ~= tonumber(ARGV[2]) then return '0' end
            local phase = redis.call('HGET', KEYS[1], 'phase')
            if phase ~= 'OPEN' then return '0' end
            local deadline = tonumber(redis.call('HGET', KEYS[1], 'deadlineAt'))
            local trackIndex = tonumber(redis.call('HGET', KEYS[1], 'trackIndex'))
            local ttl = tonumber(ARGV[5])
            $NOW_MS
            if now > deadline then return '-1' end
            if redis.call('ZSCORE', KEYS[3], ARGV[3]) then
                redis.call('ZINCRBY', KEYS[3], 1, ARGV[3])
            end
            local newSeq = seq + 1
            local revealDeadline = now + tonumber(ARGV[4])
            redis.call('HSET', KEYS[1], 'roundSeq', newSeq, 'phase', 'REVEAL',
                'deadlineAt', revealDeadline, 'winnerId', ARGV[3])
            redis.call('EXPIRE', KEYS[1], ttl)
            redis.call('ZADD', KEYS[2], revealDeadline, ARGV[1])
            return '1|REVEAL|' .. newSeq .. '|' .. trackIndex .. '|' .. revealDeadline .. '|' .. ARGV[3]
            """.trimIndent(),
            String::class.java,
        )

        // 포기 토글. 결과: 전이면 "1|…", 진행만 갱신이면 "2|<passed>|<required>|<passing>|<seq>", no-op은 "0"·"-1".
        // 임계 판정은 이번 호출이 포기를 **켠** 경우에만 한다 — 취소로 카운트가 줄어드는 호출이 전이를 일으킬 수는 없다.
        private val TOGGLE_PASS_SCRIPT = DefaultRedisScript(
            """
            if redis.call('EXISTS', KEYS[1]) == 0 then return '0' end
            local seq = tonumber(redis.call('HGET', KEYS[1], 'roundSeq'))
            if seq ~= tonumber(ARGV[2]) then return '0' end
            local phase = redis.call('HGET', KEYS[1], 'phase')
            if phase ~= 'OPEN' then return '0' end
            local deadline = tonumber(redis.call('HGET', KEYS[1], 'deadlineAt'))
            local trackIndex = tonumber(redis.call('HGET', KEYS[1], 'trackIndex'))
            local revealMillis = tonumber(ARGV[4])
            local ttl = tonumber(ARGV[5])
            local numerator = tonumber(ARGV[6])
            local denominator = tonumber(ARGV[7])
            $NOW_MS
            if now > deadline then return '-1' end
            $RESET_STALE_PASSES
            local passing = 0
            if redis.call('SISMEMBER', KEYS[4], ARGV[3]) == 1 then
                redis.call('SREM', KEYS[4], ARGV[3])
            else
                redis.call('SADD', KEYS[4], ARGV[3])
                redis.call('EXPIRE', KEYS[4], ttl)
                passing = 1
            end
            local remaining = redis.call('ZCARD', KEYS[3])
            local passed = redis.call('SCARD', KEYS[4])
            local required = math.floor((remaining * numerator + denominator - 1) / denominator)
            if passing == 1 and remaining > 0 and passed * denominator >= remaining * numerator then
                $REVEAL_ON_PASS
            end
            return '2|' .. passed .. '|' .. required .. '|' .. passing .. '|' .. seq
            """.trimIndent(),
            String::class.java,
        )

        // 게임 중 퇴장 — 점수판 제거·포기 집합 제거·임계 재판정을 하나의 원자 연산으로 수행한다.
        // 라운드가 없으면 점수판 제거만 하고 끝난다. 마감 시각은 보지 않는다 — 마감 후 남은 창에서 전이하더라도
        // sweeper가 할 일을 대신하는 것이라 결과가 같고, (roundSeq, phase) CAS가 이중 전이를 막는다.
        private val ON_PLAYER_LEFT_SCRIPT = DefaultRedisScript(
            """
            redis.call('ZREM', KEYS[3], ARGV[2])
            redis.call('SREM', KEYS[4], ARGV[2])
            if redis.call('EXISTS', KEYS[1]) == 0 then return '0' end
            local seq = tonumber(redis.call('HGET', KEYS[1], 'roundSeq'))
            local phase = redis.call('HGET', KEYS[1], 'phase')
            if phase ~= 'OPEN' then return '0' end
            local trackIndex = tonumber(redis.call('HGET', KEYS[1], 'trackIndex'))
            local revealMillis = tonumber(ARGV[3])
            local ttl = tonumber(ARGV[4])
            local numerator = tonumber(ARGV[5])
            local denominator = tonumber(ARGV[6])
            $NOW_MS
            $RESET_STALE_PASSES
            local remaining = redis.call('ZCARD', KEYS[3])
            local passed = redis.call('SCARD', KEYS[4])
            local required = math.floor((remaining * numerator + denominator - 1) / denominator)
            if remaining > 0 and passed * denominator >= remaining * numerator then
                $REVEAL_ON_PASS
            end
            return '2|' .. passed .. '|' .. required .. '|0|' .. seq
            """.trimIndent(),
            String::class.java,
        )

        // 정답 판정 게이트가 쓰는 읽기 경로 — `passSeq == roundSeq` 유효성과 SISMEMBER를 한 번의 원자 실행으로 본다.
        // 별도 왕복으로 나누면 그 사이에 라운드가 전이돼 판정이 뒤집힐 수 있다.
        private val IS_PASSING_SCRIPT = DefaultRedisScript(
            """
            if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
            local passSeq = redis.call('HGET', KEYS[1], 'passSeq')
            if not passSeq or passSeq ~= redis.call('HGET', KEYS[1], 'roundSeq') then return 0 end
            return redis.call('SISMEMBER', KEYS[2], ARGV[1])
            """.trimIndent(),
            Long::class.javaObjectType,
        )

        @Suppress("UNCHECKED_CAST")
        private val FIND_DUE_SCRIPT = DefaultRedisScript(
            """
            $NOW_MS
            return redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', now)
            """.trimIndent(),
            List::class.java,
        ) as DefaultRedisScript<List<*>>

        private val TEARDOWN_SCRIPT = DefaultRedisScript(
            """
            redis.call('DEL', KEYS[1])
            redis.call('ZREM', KEYS[2], ARGV[1])
            redis.call('DEL', KEYS[3])
            redis.call('DEL', KEYS[4])
            return 1
            """.trimIndent(),
            Long::class.javaObjectType,
        )
    }
}
