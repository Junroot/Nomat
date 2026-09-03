package ilpak.nomat.room.application.domain

/**
 * `OPEN` 라운드의 포기 현황이 갱신됐음을 알리는 ephemeral 브로드캐스트 이벤트.
 *
 * **인원수만 담는다.** 누가 눌렀는지는 방송하지 않는다 — 목록을 내리면 눈치가 보여 첫 클릭이 늦어지고
 * 포기가 사회적 낙인이 된다. 본인 여부는 클라이언트가 자기 조작으로 알고, 재접속 시에는 라운드 스냅샷의
 * `passed` 불리언으로 받는다.
 *
 * 임계에 도달해 라운드가 전이된 호출은 이 이벤트를 발행하지 않고 `RoundRevealedEvent`만 발행한다.
 * 둘이 함께 나가면 클라이언트가 `REVEAL`로 넘어간 뒤 포기 카운트를 다시 그리게 된다.
 *
 * 이벤트 클래스를 `application/domain/`에 두는 것은 Modulith 직렬화 안정성 관례를 따르는 것이며,
 * 본 이벤트는 `@TransactionalEventListener` ephemeral broadcast 경로라 outbox 적재 대상이 아니다.
 */
data class RoundPassUpdatedEvent(
    val roomId: Long,
    val roundSeq: Long,
    val passedCount: Int,
    val requiredCount: Int,
)
