package ilpak.nomat.room.application.domain

/**
 * 라운드가 `OPEN`으로 열렸음을 알리는 ephemeral 브로드캐스트 이벤트.
 * 정답(`title`·`additionalTitles`)은 싣지 않는다(answer-stripped) — 재생 참조만 전달한다.
 *
 * ⚠️ `roundSeq`와 `roundNumber`는 다르다. `roundSeq`는 전이마다 +1 되는 **CAS epoch**로
 * 라운드당 2씩 증가하므로(OPEN→REVEAL, REVEAL→다음 OPEN) 화면에 보여줄 값이 아니다.
 * 사람이 읽는 라운드 번호는 `roundNumber`(= `trackIndex + 1`)이며 `totalRounds`와 짝을 이룬다.
 */
data class RoundStartedEvent(
    val roomId: Long,
    val roundSeq: Long,
    val roundNumber: Int,
    val totalRounds: Int,
    val deadlineAt: Long,
    val embedId: String,
    val startTimeSec: Int,
    val endTimeSec: Int,
    val repeatCount: Int,
)
