package ilpak.nomat.room.application.domain

/**
 * 라운드가 `OPEN`으로 열렸음을 알리는 ephemeral 브로드캐스트 이벤트.
 * 정답(`title`·`additionalTitles`)은 싣지 않는다(answer-stripped) — 재생 참조만 전달한다.
 */
data class RoundStartedEvent(
    val roomId: Long,
    val roundSeq: Long,
    val totalRounds: Int,
    val deadlineAt: Long,
    val embedId: String,
    val startTimeSec: Int,
    val endTimeSec: Int,
    val repeatCount: Int,
)
