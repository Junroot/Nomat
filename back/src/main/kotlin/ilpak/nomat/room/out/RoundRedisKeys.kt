package ilpak.nomat.room.out

/**
 * 라운드 엔진 Redis 키 스킴 — **클러스터 모드 안전**.
 *
 * 라운드 전이는 방의 상태 Hash·점수판 ZSET·전역 마감 인덱스 ZSET을 하나의 원자 Lua로 함께 조작한다.
 * Redis 클러스터는 멀티 키 명령의 모든 키가 같은 hash slot에 있어야 하며(아니면 `CROSSSLOT` 거부),
 * slot은 키의 `{...}` hash tag 부분만으로 결정된다. 따라서 한 방의 모든 키와 그 방이 속한 마감 인덱스
 * 샤드를 **동일 hash tag `{shard}`**로 묶어 같은 slot·같은 노드에 배치한다. `shard = roomId mod SHARD_COUNT`.
 *
 * - round:   `round:{shard}:<roomId>`   (Hash)  — 라운드 상태(전이 게이트의 source of truth)
 * - scores:  `scores:{shard}:<roomId>`  (ZSET)  — 점수판
 * - 마감 인덱스: `rounds:deadlines:{shard}` (ZSET, member=roomId, score=deadlineAt) — sweeper 조회 대상
 *
 * 이렇게 하면 한 방의 세 키가 항상 같은 slot이라 기존 원자 Lua CAS·단일 시계(`redis.call('TIME')`) 설계를
 * 그대로 유지한 채 클러스터에서도 동작한다(방은 샤드를 넘나들지 않으므로 per-shard로 단일 시계가 성립).
 * 단일 인스턴스/마스터-레플리카에서는 hash tag가 그냥 리터럴 문자라 동작에 영향이 없다.
 *
 * `out` 어댑터 클래스는 `private`이지만 이 키 스킴은 화이트박스 통합 테스트가 "마감을 과거로 당기는" 등의
 * 조작을 위해 동일 키를 계산해야 하므로 모듈 내부(`internal`)로만 공개한다(패키지 외부 도메인으로는 미노출).
 */
internal object RoundRedisKeys {

    /**
     * 마감 인덱스 샤드 수 = 라운드 키가 흩어지는 slot 그룹 수. 값을 키우면 클러스터 노드 간 분산도가 좋아지지만
     * sweeper가 틱마다 조회하는 인덱스 키 수도 그만큼 늘어난다(분산 ↔ sweeper 팬아웃 트레이드오프).
     * 휘발성 라운드 상태의 물리 배치를 결정하므로, 진행 중 게임이 있는 동안 값을 바꾸면 마감 인덱스와
     * 라운드 Hash가 서로 다른 샤드로 갈려 정합이 깨진다 — 변경 배포는 활성 게임이 없는 시점에만 한다.
     */
    const val SHARD_COUNT = 64

    fun shardOf(roomId: Long): Int = Math.floorMod(roomId, SHARD_COUNT.toLong()).toInt()

    /** 방 라운드 상태 Hash 키. */
    fun round(roomId: Long): String = "round:{${shardOf(roomId)}}:$roomId"

    /** 방 점수판 ZSET 키. */
    fun scores(roomId: Long): String = "scores:{${shardOf(roomId)}}:$roomId"

    /** 방이 속한 마감 인덱스 샤드 ZSET 키(방 키들과 동일 slot). */
    fun deadlines(roomId: Long): String = deadlinesShard(shardOf(roomId))

    /** 샤드 인덱스로 직접 마감 인덱스 ZSET 키를 만든다(sweeper의 전 샤드 순회용). */
    fun deadlinesShard(shard: Int): String = "rounds:deadlines:{$shard}"
}
