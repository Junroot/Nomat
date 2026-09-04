package ilpak.nomat.room.out

import ilpak.nomat.room.application.domain.PendingLeave
import ilpak.nomat.room.application.domain.PendingLeaveStore
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Repository

/**
 * 퇴장 유예 예약 어댑터 — Redis ZSET 하나([PendingLeaveRedisKeys.PENDING_LEAVES])에 예약을 둔다.
 *
 * 시각을 다루는 연산(예약·만료 조회·복원)은 Lua 안에서 `redis.call('TIME')`을 읽어 **Redis 단일 시계**를 쓴다
 * ([RoundStateStoreImpl]과 같은 프리앰블). 앱 시계를 쓰면 replica 간 스큐가 "A가 예약한 항목을 B의 sweeper가
 * 몇 초 이르게/늦게 처리"로 나타난다.
 *
 * [remove]는 단순 `ZREM`이지만 반환값이 곧 소유권이다 — 재접속(취소)과 sweeper(claim)가 같은 항목을 두고
 * 경합해도 정확히 한쪽만 1을 받는다.
 */
@Repository
private class PendingLeaveStoreImpl(
    private val redisTemplate: StringRedisTemplate,
) : PendingLeaveStore {

    override fun schedule(roomId: Long, playerId: Long, graceSeconds: Long) {
        redisTemplate.execute(
            SCHEDULE_SCRIPT,
            listOf(PendingLeaveRedisKeys.PENDING_LEAVES),
            PendingLeaveRedisKeys.member(roomId, playerId),
            (graceSeconds * MILLIS_PER_SECOND).toString(),
        )
    }

    override fun remove(roomId: Long, playerId: Long): Boolean {
        val removed = redisTemplate.opsForZSet()
            .remove(PendingLeaveRedisKeys.PENDING_LEAVES, PendingLeaveRedisKeys.member(roomId, playerId))
        return removed == 1L
    }

    override fun findDue(): List<PendingLeave> {
        val members = redisTemplate.execute(FIND_DUE_SCRIPT, listOf(PendingLeaveRedisKeys.PENDING_LEAVES))
            ?: return emptyList()
        return members.mapNotNull { raw ->
            val (roomId, playerId) = PendingLeaveRedisKeys.parseMember(raw.toString()) ?: return@mapNotNull null
            PendingLeave(roomId, playerId)
        }
    }

    override fun restore(roomId: Long, playerId: Long) {
        redisTemplate.execute(
            SCHEDULE_SCRIPT,
            listOf(PendingLeaveRedisKeys.PENDING_LEAVES),
            PendingLeaveRedisKeys.member(roomId, playerId),
            RETRY_DELAY_MS.toString(),
        )
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1_000L

        /**
         * claim 후 퇴장 실패 시 다시 만료되기까지의 간격. 원래 만료 시각으로 되돌리면 실패 항목이 매 틱(1초)
         * 재시도되어 락 경합 중인 방에 로그가 초당 한 줄씩 쌓이고, 항목이 과거 score에 머물러 노화한다.
         * 재시도 상한과 시간 기반 GC는 두지 않는다 — 항목은 claim 또는 취소로만 소멸하며, 지속 실패는
         * 이 간격마다 남는 경고 로그로 드러나야 할 운영 이슈다.
         */
        private const val RETRY_DELAY_MS = 5_000L

        private const val NOW_MS =
            "local t = redis.call('TIME'); local now = (tonumber(t[1]) * 1000) + math.floor(tonumber(t[2]) / 1000)"

        /** KEYS[1]=ZSET, ARGV[1]=member, ARGV[2]=지금부터의 지연(ms). 예약과 복원이 같은 스크립트를 쓴다. */
        private val SCHEDULE_SCRIPT = DefaultRedisScript(
            """
            $NOW_MS
            redis.call('ZADD', KEYS[1], now + tonumber(ARGV[2]), ARGV[1])
            return 1
            """.trimIndent(),
            Long::class.javaObjectType,
        )

        /** KEYS[1]=ZSET. 만료 시각이 지난 member 목록(score 불필요). */
        private val FIND_DUE_SCRIPT = DefaultRedisScript(
            """
            $NOW_MS
            return redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', now)
            """.trimIndent(),
            List::class.java,
        )
    }
}
