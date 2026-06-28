package ilpak.nomat.room.out

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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
 * 키:
 * - `room:{id}:round` (Hash): roundSeq·phase·deadlineAt·trackIndex·winnerId·totalRounds·trackOrder·trackDurations
 * - `rounds:deadlines` (전역 ZSET): score=deadlineAt(ms), member=roomId
 * - `room:{id}:scores` (ZSET): member=playerId, score=누적 점수
 */
@Repository
private class RoundStateStoreImpl(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) : RoundStateStore {

    override fun start(roomId: Long, tracks: List<RoundTrackSpec>, playerIds: Set<Long>): RoundTransition {
        val raw = redisTemplate.execute(
            START_SCRIPT,
            listOf(roundKey(roomId), DEADLINES_KEY, scoresKey(roomId)),
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
            listOf(roundKey(roomId), DEADLINES_KEY),
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
            listOf(roundKey(roomId), DEADLINES_KEY, scoresKey(roomId)),
            roomId.toString(),
            expectedSeq.toString(),
            winnerId.toString(),
            REVEAL_MILLIS.toString(),
            TTL_SECONDS.toString(),
        )
        return parse(raw)
    }

    override fun snapshot(roomId: Long): RoundSnapshot? {
        val hash = redisTemplate.opsForHash<String, String>().entries(roundKey(roomId))
        if (hash.isEmpty()) {
            return null
        }
        val trackOrder = objectMapper.readValue<List<Long>>(hash.getValue("trackOrder"))
        val trackIndex = hash.getValue("trackIndex").toInt()
        return RoundSnapshot(
            phase = RoundPhase.valueOf(hash.getValue("phase")),
            roundSeq = hash.getValue("roundSeq").toLong(),
            totalRounds = hash.getValue("totalRounds").toInt(),
            trackIndex = trackIndex,
            trackOrder = trackOrder,
            currentTrackId = trackOrder[trackIndex],
            deadlineAt = hash.getValue("deadlineAt").toLong(),
            winnerId = hash["winnerId"]?.takeIf { it.isNotEmpty() }?.toLong(),
            scores = scoreboard(roomId),
        )
    }

    override fun findDueRoomIds(): List<Long> {
        val members = redisTemplate.execute(FIND_DUE_SCRIPT, listOf(DEADLINES_KEY)) ?: return emptyList()
        return members.map { it.toString().toLong() }
    }

    override fun scoreboard(roomId: Long): List<ScoreEntry> {
        val entries = redisTemplate.opsForZSet().reverseRangeWithScores(scoresKey(roomId), 0, -1)
            ?: return emptyList()
        return entries.mapNotNull { tuple ->
            val playerId = tuple.value?.toLong() ?: return@mapNotNull null
            ScoreEntry(playerId, tuple.score?.toInt() ?: 0)
        }
    }

    override fun removeScore(roomId: Long, playerId: Long) {
        redisTemplate.opsForZSet().remove(scoresKey(roomId), playerId.toString())
    }

    override fun teardown(roomId: Long) {
        redisTemplate.execute(
            TEARDOWN_SCRIPT,
            listOf(roundKey(roomId), DEADLINES_KEY, scoresKey(roomId)),
            roomId.toString(),
        )
    }

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

    private fun roundKey(roomId: Long): String = "room:$roomId:round"
    private fun scoresKey(roomId: Long): String = "room:$roomId:scores"

    companion object {
        private const val DEADLINES_KEY = "rounds:deadlines"
        private const val REVEAL_MILLIS = 5_000L
        private const val TTL_SECONDS = 86_400L
        private const val IGNORED_CODE = "0"
        private const val NOT_DUE_CODE = "-1"

        // Redis TIME([초, 마이크로초])을 ms epoch로 환산하는 공통 조각.
        private const val NOW_MS =
            "local t = redis.call('TIME'); local now = (tonumber(t[1]) * 1000) + math.floor(tonumber(t[2]) / 1000)"

        // 결과 포맷: "1|<phase>|<seq>|<trackIndex>|<deadlineAt>|<winnerId>" / no-op은 "0"·"-1".
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
            return 1
            """.trimIndent(),
            Long::class.javaObjectType,
        )
    }
}
