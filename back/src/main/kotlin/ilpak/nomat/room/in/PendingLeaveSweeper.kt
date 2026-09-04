package ilpak.nomat.room.`in`

import ilpak.nomat.room.application.RoomService
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 퇴장 유예 만료의 유일 구동기. [RoundDeadlineSweeper]와 같은 구조로, 단일 replica만(`@SchedulerLock`)
 * `rooms:pending-leaves` ZSET에서 만료 항목을 모아 [RoomService.sweepDueLeaves]로 넘긴다.
 * 인-프로세스 타이머가 없으므로 replica가 재시작되거나 교체돼도 예약이 사라지지 않는다.
 *
 * 한 항목의 퇴장이 멤버십 락 대기(최대 5초)로 `lockAtMostFor`(4초)를 넘기면 ShedLock이 먼저 풀려 다른 replica가
 * 겹쳐 돌 수 있다. 그래도 안전하다 — 항목은 처리 전에 `ZREM`으로 claim되므로 두 sweep이 같은 항목을 잡지 못한다.
 * 여기서 락은 중복 방지 장치가 아니라 부하 분산 장치다.
 */
@Component
private class PendingLeaveSweeper(
    private val roomService: RoomService,
) {

    @Scheduled(fixedDelay = POLL_INTERVAL_MS)
    @SchedulerLock(name = "pending-leave-sweep", lockAtMostFor = "PT4S")
    fun sweep() {
        roomService.sweepDueLeaves()
    }

    companion object {
        private const val POLL_INTERVAL_MS: Long = 1_000
    }
}
