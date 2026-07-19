package ilpak.nomat.room.application.dto

import ilpak.nomat.room.application.domain.RoomPlaylistTrack
import ilpak.nomat.room.application.domain.RoundPhase

/**
 * 재접속 복원용 라운드 스냅샷. 단계로 게이팅되어 `OPEN` 중에는 정답(`title`)을 제외하고
 * `REVEAL`·`ENDED`에서만 포함한다. 재생 참조는 answer-stripped다.
 *
 * `nextTrack`은 `REVEAL` 단계에서만 채워진다. `REVEAL` 중 재접속한 멤버는 `ROUND_REVEALED`
 * 이벤트를 놓쳐 혼자만 선버퍼링을 못 하는데, 그러면 그 멤버만 다음 라운드에서 로드·버퍼링 지연을
 * 온전히 부담해 선버퍼링이 없애려는 참가자 간 불균등이 그대로 재현된다. 마지막 라운드에서는 null.
 */
data class RoundSnapshotResponse(
    val phase: RoundPhase,
    val roundSeq: Long,
    // 화면 표기용 라운드 번호(= `trackIndex + 1`). `roundSeq`는 전이마다 +1 되는 CAS epoch라
    // 라운드당 2씩 증가하므로 표기에 쓰면 안 된다.
    val roundNumber: Int,
    val totalRounds: Int,
    val deadlineAt: Long,
    val currentTrack: RoundTrackRefResponse,
    val title: String?,
    val winnerId: Long?,
    val scores: List<ScoreEntryResponse>,
    val nextTrack: RoundTrackRefResponse? = null,
)

data class RoundTrackRefResponse(
    val embedId: String,
    val startTimeSec: Int,
    val endTimeSec: Int,
    val repeatCount: Int,
) {
    companion object {
        fun of(track: RoomPlaylistTrack) = RoundTrackRefResponse(
            track.embedId,
            track.startTimeSec,
            track.endTimeSec,
            track.repeatCount,
        )
    }
}

data class ScoreEntryResponse(
    val playerId: Long,
    val score: Int,
)
