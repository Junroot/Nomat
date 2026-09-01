package ilpak.nomat.room.application.dto

/**
 * `ROUND_REVEALED` 브로드캐스트. 이 단계에서만 정답(`title`)과 승자·갱신된 점수판을 공개한다.
 * 승자가 없으면(타임아웃) `winnerId`는 null. 행위자 개념이 없어 `playerId`/`nickname`은 null.
 *
 * 점수판 항목은 닉네임을 함께 담고, 승자 닉네임(`winnerNickname`)도 별도로 싣는다. 클라이언트가
 * 현재 멤버 목록으로 이름을 되짚으면 이미 떠난 참가자가 이름을 잃기 때문이다(이슈 #235).
 * 승자를 점수판에서 역참조하지 않는 이유는 점수 항목이 없는 승자가 성립하기 때문이다.
 *
 * `nextTrack`은 클라이언트가 REVEAL 구간 동안 선버퍼링할 다음 라운드의 answer-stripped 재생
 * 참조다. 마지막 라운드에서는 null.
 */
data class RoundRevealedEventMessage(
    override val roomId: Long,
    val roundSeq: Long,
    val winnerId: Long?,
    val winnerNickname: String?,
    val title: String,
    val scores: List<ScoreEntryResponse>,
    val nextTrack: RoundTrackRefResponse? = null,
    override val playerId: Long? = null,
    override val nickname: String? = null,
) : RoomEventMessage
