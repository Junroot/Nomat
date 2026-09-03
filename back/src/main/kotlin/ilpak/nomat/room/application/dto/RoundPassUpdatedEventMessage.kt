package ilpak.nomat.room.application.dto

/**
 * `ROUND_PASS_UPDATED` 브로드캐스트 — 현재 `OPEN` 라운드의 포기 현황.
 *
 * **인원수만 싣는다.** 포기자 식별자·닉네임은 담지 않으므로 행위자 개념이 없는 라운드 이벤트와 같이
 * `playerId`/`nickname`은 null이다. 익명성이 첫 클릭의 심리적 비용을 낮춘다 — 누가 눌렀는지가 보이면
 * 눈치가 보여 첫 클릭이 늦어지고 포기가 사회적 낙인이 된다.
 *
 * 임계 도달로 라운드가 전이된 경우에는 이 메시지가 나가지 않고 `ROUND_REVEALED`만 나간다.
 */
data class RoundPassUpdatedEventMessage(
    override val roomId: Long,
    val roundSeq: Long,
    val passedCount: Int,
    val requiredCount: Int,
    override val playerId: Long? = null,
    override val nickname: String? = null,
) : RoomEventMessage
