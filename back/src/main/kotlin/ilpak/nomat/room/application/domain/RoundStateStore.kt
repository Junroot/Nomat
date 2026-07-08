package ilpak.nomat.room.application.domain

/**
 * 라운드 상태 포트. 모든 라운드 전이(게임 시작·첫 정답·타임아웃/공개·종료)가 통과하는 단일 진입점 계약이다.
 *
 * 전이의 멱등성은 분산 락이 아니라 라운드 상태에 대한 단일 원자 연산(`roundSeq`/`phase` 게이트)으로 보장하며,
 * 시각 판정은 Redis 단일 시계로 통일한다. 구현은 `out/`의 어댑터가 담당한다.
 */
interface RoundStateStore {

    /**
     * 게임 시작 시 라운드 순서를 고정하고 첫 라운드를 `OPEN`으로 연다.
     * 점수판은 참가자 전원을 0점으로 초기화한다(이후 멤버 조건부 가점·퇴장 제거의 기준).
     */
    fun start(roomId: Long, tracks: List<RoundTrackSpec>, playerIds: Set<Long>): RoundTransition

    /** 마감 지난 라운드를 다음 단계로 전이(타임아웃 `OPEN→REVEAL`, `REVEAL→다음 OPEN`/`ENDED`). sweeper가 구동. */
    fun tryAdvanceOnDeadline(roomId: Long, expectedSeq: Long): RoundTransition

    /** 첫 정답으로 `OPEN→REVEAL` 전이. 마감 시각 이내일 때만 인정하고, 승자가 아직 멤버이면 원자 가점한다. */
    fun tryAdvanceOnCorrect(roomId: Long, expectedSeq: Long, winnerId: Long): RoundTransition

    /** 재접속 복원·sweeper 판단용 현재 라운드 스냅샷. 라운드 상태가 없으면 null. */
    fun snapshot(roomId: Long): RoundSnapshot?

    /** Redis 단일 시계 기준 마감 지난 라운드의 roomId 목록. */
    fun findDueRoomIds(): List<Long>

    /** 현재 점수판(내림차순). */
    fun scoreboard(roomId: Long): List<ScoreEntry>

    /** 게임 중 퇴장 시 점수판에서 해당 플레이어 제거. */
    fun removeScore(roomId: Long, playerId: Long)

    /** 라운드 상태·마감 등록·점수판을 원자적으로 정리(게임 종료·방 삭제). */
    fun teardown(roomId: Long)
}

/** 게임 시작 시 고정되는 라운드별 트랙 식별자와 `OPEN` 지속 시간(ms). */
data class RoundTrackSpec(
    val trackId: Long,
    val openDurationMillis: Long,
)

enum class TransitionResult {
    /** 전이 성공(이 호출이 epoch를 전진시킴). */
    TRANSITIONED,

    /** 기대 `roundSeq`/`phase` 불일치 — 다른 트리거가 이미 전이했거나 라운드가 없음(no-op). */
    IGNORED,

    /** 마감 전 조기 발화 또는 마감 후 늦은 정답(no-op, 자가 보정). */
    NOT_DUE,
}

data class RoundTransition(
    val result: TransitionResult,
    val phase: RoundPhase? = null,
    val roundSeq: Long = 0,
    val trackIndex: Int = 0,
    val deadlineAt: Long = 0,
    val winnerId: Long? = null,
)

data class RoundSnapshot(
    val phase: RoundPhase,
    val roundSeq: Long,
    val totalRounds: Int,
    val trackIndex: Int,
    val trackOrder: List<Long>,
    val currentTrackId: Long,
    val deadlineAt: Long,
    val winnerId: Long?,
    val scores: List<ScoreEntry>,
)

data class ScoreEntry(
    val playerId: Long,
    val score: Int,
)
