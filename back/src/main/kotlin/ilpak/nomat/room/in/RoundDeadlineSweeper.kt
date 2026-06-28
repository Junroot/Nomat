package ilpak.nomat.room.`in`

import ilpak.nomat.room.application.RoundService
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 타임아웃·정답 공개 전이의 유일 구동기. 단일 replica만(`@SchedulerLock`) `rounds:deadlines` ZSET에서
 * 마감 지난 미전이 라운드를 찾아 `RoundService.tryAdvance`로 전이한다
 * (`EventPublicationRetryScheduler` 패턴). 별도 로컬 타이머가 없어 replica마다 타이머가 중복되지 않는다.
 *
 * 첫 정답(정밀 필요)은 sweeper가 아니라 채팅 메시지가 즉시 처리하므로(이벤트 구동) 폴링 지터와 무관하다.
 * sweeper가 주 구동기이므로 락 홀더 사망 시 그만큼 자동 진행이 멈춘다 — `PT1M`을 복제하지 않고
 * `lockAtMostFor`를 폴링 주기의 2~4배(`PT4S`)로 짧게 잡아 회복을 몇 초로 유계화한다(Decision 8).
 */
@Component
private class RoundDeadlineSweeper(
    private val roundService: RoundService,
) {

    @Scheduled(fixedDelay = POLL_INTERVAL_MS)
    @SchedulerLock(name = "round-deadline-sweep", lockAtMostFor = "PT4S")
    fun sweep() {
        roundService.sweepDueRounds()
    }

    companion object {
        private const val POLL_INTERVAL_MS: Long = 1_000
    }
}
