package ilpak.nomat.room.application.dto

/**
 * `ROUND_STARTED` 브로드캐스트. answer-stripped — 정답(`title`·`additionalTitles`)은 싣지 않고
 * 재생 참조(`embedId`·재생 구간·`repeatCount`)만 전달한다. 행위자가 없어 `playerId`/`nickname`은 null.
 *
 * ⚠️ 화면 표기에 쓸 값은 `roundSeq`가 아니라 `roundNumber`다 — `roundSeq`는 전이마다 +1 되는
 * CAS epoch라 라운드당 2씩 증가한다(`RoundStartedEvent` 주석 참조).
 */
data class RoundStartedEventMessage(
    override val roomId: Long,
    val roundSeq: Long,
    val roundNumber: Int,
    val totalRounds: Int,
    val deadlineAt: Long,
    val embedId: String,
    val startTimeSec: Int,
    val endTimeSec: Int,
    val repeatCount: Int,
    override val playerId: Long? = null,
    override val nickname: String? = null,
) : RoomEventMessage
