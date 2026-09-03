package ilpak.nomat.room.application.domain

/**
 * 라운드 상태 포트. 모든 라운드 전이(게임 시작·첫 정답·포기 임계·타임아웃/공개·종료)가 통과하는 단일 진입점 계약이다.
 *
 * 전이의 멱등성은 분산 락이 아니라 라운드 상태에 대한 단일 원자 연산(`roundSeq`/`phase` 게이트)으로 보장하며,
 * 시각 판정은 Redis 단일 시계로 통일한다. 구현은 `out/`의 어댑터가 담당한다.
 */
interface RoundStateStore {

    /**
     * 게임 시작 시 라운드 순서를 고정하고 첫 라운드를 `OPEN`으로 연다.
     * 점수판은 참가자 전원을 0점으로 초기화한다(이후 멤버 조건부 가점·퇴장 제거의 기준).
     *
     * 이전 게임의 포기 상태도 함께 폐기한다 — `roundSeq`가 1로 되돌아가는 게임 경계에서는
     * 포기 집합의 lazy reset 판별식(`passSeq != roundSeq`)이 성립하지 않기 때문이다.
     */
    fun start(roomId: Long, tracks: List<RoundTrackSpec>, playerIds: Set<Long>): RoundTransition

    /** 마감 지난 라운드를 다음 단계로 전이(타임아웃 `OPEN→REVEAL`, `REVEAL→다음 OPEN`/`ENDED`). sweeper가 구동. */
    fun tryAdvanceOnDeadline(roomId: Long, expectedSeq: Long): RoundTransition

    /** 첫 정답으로 `OPEN→REVEAL` 전이. 마감 시각 이내일 때만 인정하고, 승자가 아직 멤버이면 원자 가점한다. */
    fun tryAdvanceOnCorrect(roomId: Long, expectedSeq: Long, winnerId: Long): RoundTransition

    /**
     * 포기 토글 → 임계 도달 시 `OPEN→REVEAL` CAS.
     *
     * 같은 호출이 켜고 끄기를 겸한다(이미 포기 중이면 해제). 토글 뒤 남은 인원 대비 포기 인원이
     * 임계에 도달했고 이번 호출이 포기를 **켠** 경우에만 라운드를 승자 없이 `REVEAL`로 전이한다.
     *
     * `expectedSeq`는 라운드 경계에서 늦게 도착한 신호를 차단하는 게이트다. 마감 직전에 보낸 포기가
     * 전이 직후에 도착하면 아직 곡이 들리지도 않은 **다음 라운드**에 표가 꽂히므로, 불일치는
     * `IGNORED`로 흘린다(`tryAdvanceOnCorrect`와 동일한 규약). 마감 시각 이후 도착은 `NOT_DUE`.
     */
    fun togglePass(roomId: Long, expectedSeq: Long, playerId: Long): PassOutcome

    /**
     * 게임 중 퇴장 처리 — 점수판 제거·포기 집합 제거·임계 재판정을 **하나의 원자 연산**으로 수행한다.
     *
     * 로스터가 줄면 임계도 함께 내려가므로, 아무도 새로 누르지 않았는데 임계가 충족되는 상태가 성립한다
     * (5명/임계 4/포기 3에서 미포기자 1명이 나가면 4명/임계 3/포기 3 → 도달). 셋을 나누어 호출하면
     * 그 사이에 임계가 충족된 채 아무도 재판정하지 않는 창이 생긴다.
     *
     * [togglePass]와 달리 `expectedSeq`를 받지 않는다 — 호출 지점(`RoomLeftEvent` 핸들러)이 현재
     * 라운드를 모르기 때문이다. 알아내려고 스냅샷을 먼저 읽으면 읽기와 쓰기 사이에 라운드가 전이될 수
     * 있어 오히려 경합이 생기므로, 스크립트가 현재 `roundSeq`를 직접 읽고 그 안에서 전부 끝낸다.
     */
    fun onPlayerLeft(roomId: Long, playerId: Long): PassOutcome

    /**
     * 지금 이 참가자가 현재 라운드의 포기 상태인지 — 정답 판정 게이트가 쓰는 읽기 경로.
     *
     * 포기 집합은 **`passSeq == roundSeq`일 때만 유효하다**(lazy reset 계약). 불일치면 이전 라운드의
     * 잔재이므로 포기 상태가 아닌 것으로 본다. 유효성 판정과 멤버십 조회는 **하나의 원자 연산**이어야
     * 한다 — 별도 왕복으로 나누면 그 사이에 라운드가 전이돼 판정이 뒤집힐 수 있다.
     */
    fun isPassing(roomId: Long, playerId: Long): Boolean

    /**
     * 재접속 복원·sweeper 판단용 현재 라운드 스냅샷. 라운드 상태가 없으면 null.
     *
     * [viewerId]를 주면 그 참가자 기준으로 [RoundSnapshot.passing]을 채운다. 누가 포기했는지는
     * 공개하지 않으므로(인원수만 방송한다) 본인 여부는 서버가 판정해 불리언으로만 내려준다.
     */
    fun snapshot(roomId: Long, viewerId: Long? = null): RoundSnapshot?

    /** Redis 단일 시계 기준 마감 지난 라운드의 roomId 목록. */
    fun findDueRoomIds(): List<Long>

    /** 현재 점수판(내림차순). */
    fun scoreboard(roomId: Long): List<ScoreEntry>

    /** 라운드 상태·마감 등록·점수판·포기 집합을 원자적으로 정리(게임 종료·방 삭제). */
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

/**
 * 포기 토글·퇴장 재평가의 결과.
 *
 * `transition.result == TRANSITIONED`이면 임계 도달로 라운드가 `REVEAL`로 전이된 경우다. **그때는
 * 포기 현황을 별도로 전파하지 않는다** — `ROUND_REVEALED`와 포기 현황이 함께 나가면 클라이언트가
 * `REVEAL`로 넘어간 뒤 포기 카운트를 다시 그리게 된다. 따라서 전이 시 [passedCount]·[requiredCount]는
 * 의미를 갖지 않고 0이다.
 *
 * `IGNORED`(라운드 없음/`roundSeq` 불일치/`OPEN` 아님)·`NOT_DUE`(마감 이후)면 상태가 바뀌지 않았으므로
 * 아무것도 전파하지 않는다.
 */
data class PassOutcome(
    val transition: RoundTransition,
    val roundSeq: Long = 0,
    val passedCount: Int = 0,
    val requiredCount: Int = 0,
    val passing: Boolean = false,
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
    val passedCount: Int = 0,
    val requiredCount: Int = 0,
    // 조회자(`viewerId`) 기준. 조회자를 주지 않으면 항상 false.
    val passing: Boolean = false,
) {
    /**
     * 다음 라운드 트랙 식별자. 마지막 라운드에서는 null.
     *
     * `trackOrder`는 게임 시작 시 셔플되어 고정되므로 다음 트랙은 이미 결정돼 있다 —
     * 추가 조회나 상태 없이 현재 인덱스의 다음 항목으로 얻는다. 클라이언트가 REVEAL 구간에
     * 다음 곡을 미리 버퍼링할 수 있도록 전달하는 데 쓴다.
     */
    fun nextTrackId(): Long? = trackOrder.getOrNull(trackIndex + 1)
}

data class ScoreEntry(
    val playerId: Long,
    val score: Int,
)
