package ilpak.nomat.room.application.dto

import ilpak.nomat.room.application.domain.RoundPhase

/**
 * 재접속 복원용 라운드 스냅샷. 단계로 게이팅되어 `OPEN` 중에는 정답(`title`)을 제외하고
 * `REVEAL`·`ENDED`에서만 포함한다. 재생 참조는 answer-stripped다.
 */
data class RoundSnapshotResponse(
    val phase: RoundPhase,
    val roundSeq: Long,
    val totalRounds: Int,
    val deadlineAt: Long,
    val currentTrack: RoundTrackRefResponse,
    val title: String?,
    val winnerId: Long?,
    val scores: List<ScoreEntryResponse>,
)

data class RoundTrackRefResponse(
    val embedId: String,
    val startTimeSec: Int,
    val endTimeSec: Int,
    val repeatCount: Int,
)

data class ScoreEntryResponse(
    val playerId: Long,
    val score: Int,
)
