package ilpak.nomat.room.application.domain

/**
 * 라운드가 `REVEAL`로 전이됐음을 알리는 ephemeral 브로드캐스트 이벤트.
 * 이 단계에서만 정답(`title`)과 승자·갱신된 점수판을 공개한다. 승자가 없으면(타임아웃) `winnerId`는 null.
 *
 * `nextTrack`은 클라이언트가 REVEAL 구간 동안 다음 라운드 트랙을 미리 버퍼링하도록 실어 보내는
 * answer-stripped 재생 참조다. 라운드 시작부터 소리가 나기까지의 지연이 각자의 회선·기기에 따라
 * 달라 공정성이 깨지는 문제를 없애기 위한 것으로, 마지막 라운드에는 다음 트랙이 없어 null이다.
 *
 * 이 필드가 정답 비노출 원칙을 약화시키지 않는 이유: `embedId`는 이미 `ROUND_STARTED`로 라운드
 * 시작 시점에 내려가고 있어 실제로 보호되는 것은 `title`·`additionalTitles`다. 선전달은 `embedId`가
 * 도달하는 시점을 REVEAL 구간만큼 앞당길 뿐 새 정보를 노출하지 않는다.
 */
data class RoundRevealedEvent(
    val roomId: Long,
    val roundSeq: Long,
    val winnerId: Long?,
    val title: String,
    val scores: List<ScoreEntry>,
    val nextTrack: RoundTrackRef? = null,
)

/**
 * answer-stripped 재생 참조. 정답(`title`·`additionalTitles`)은 담지 않는다.
 * 클라이언트가 재생에 필요로 하는 최소 정보만 가진다.
 */
data class RoundTrackRef(
    val embedId: String,
    val startTimeSec: Int,
    val endTimeSec: Int,
    val repeatCount: Int,
)
