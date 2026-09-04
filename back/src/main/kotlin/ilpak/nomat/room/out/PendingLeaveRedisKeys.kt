package ilpak.nomat.room.out

/**
 * 퇴장 유예 예약 Redis 키 스킴.
 *
 * - `rooms:pending-leaves` (ZSET): member = `"<roomId>:<playerId>"`, score = 만료 시각(ms, Redis `TIME` 기준)
 *
 * **샤딩하지 않는다.** 예약(`ZADD`)·취소/claim(`ZREM`)·만료 조회(`ZRANGEBYSCORE`)·복원(`ZADD`)이 전부 단일 키 연산이라
 * 클러스터 `CROSSSLOT` 제약과 무관하고, 방당 최대 20명·활성 방 수십 개 규모에서 ZSET 하나가 핫키가 될 일도 없다.
 * 샤딩은 sweeper 팬아웃만 늘린다. 나중에 라운드 키와 원자적으로 묶을 필요가 생기면 그때 [RoundRedisKeys]처럼
 * `{shard}` hash tag를 붙인다 — 키 이름을 이 객체 한 곳이 소유하는 이유다.
 *
 * `out` 어댑터는 `private class`지만 통합 테스트가 같은 키를 계산해 화이트박스로 상태를 조작해야 하므로
 * 모듈 내부(`internal`)로만 공개한다.
 */
internal object PendingLeaveRedisKeys {

    const val PENDING_LEAVES = "rooms:pending-leaves"

    private const val SEPARATOR = ":"

    fun member(roomId: Long, playerId: Long): String = "$roomId$SEPARATOR$playerId"

    /** `"<roomId>:<playerId>"` → (roomId, playerId). 형식이 어긋나면 null. */
    fun parseMember(member: String): Pair<Long, Long>? {
        val ids = member.split(SEPARATOR).map { it.toLongOrNull() }
        return if (ids.size == 2 && ids.all { it != null }) ids[0]!! to ids[1]!! else null
    }
}
