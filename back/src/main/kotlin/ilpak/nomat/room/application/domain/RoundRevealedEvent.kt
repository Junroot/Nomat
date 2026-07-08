package ilpak.nomat.room.application.domain

/**
 * 라운드가 `REVEAL`로 전이됐음을 알리는 ephemeral 브로드캐스트 이벤트.
 * 이 단계에서만 정답(`title`)과 승자·갱신된 점수판을 공개한다. 승자가 없으면(타임아웃) `winnerId`는 null.
 */
data class RoundRevealedEvent(
    val roomId: Long,
    val roundSeq: Long,
    val winnerId: Long?,
    val title: String,
    val scores: List<ScoreEntry>,
)
