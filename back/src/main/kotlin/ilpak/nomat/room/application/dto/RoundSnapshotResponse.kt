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
    // 승자 닉네임을 점수판에서 역참조하지 않고 따로 싣는다 — 가점은 아직 멤버일 때만 적용되는 반면
    // `winnerId`는 무조건 기록되므로 점수 항목이 없는 승자가 성립한다. 타임아웃 공개에서는 null.
    val winnerNickname: String?,
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

/**
 * 점수판 항목. `nickname`은 서버가 `player` 저장소에서 해석해 실어 보내는 값이라, 클라이언트가
 * 현재 멤버 목록과 조인할 필요가 없다 — 이미 방을 떠난 참가자도 이름이 유지된다.
 */
data class ScoreEntryResponse(
    val playerId: Long,
    val nickname: String,
    val score: Int,
)
