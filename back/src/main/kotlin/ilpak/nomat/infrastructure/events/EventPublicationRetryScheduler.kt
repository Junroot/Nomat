package ilpak.nomat.infrastructure.events

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.modulith.events.IncompleteEventPublications
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration

@Component
private class EventPublicationRetryScheduler(
    private val incompleteEventPublications: IncompleteEventPublications,
) {

    @Scheduled(fixedDelay = RETRY_INTERVAL_MS)
    @SchedulerLock(name = "event-publication-retry", lockAtMostFor = "PT1M")
    fun retryIncomplete() {
        incompleteEventPublications.resubmitIncompletePublicationsOlderThan(MIN_AGE_BEFORE_RETRY)
    }

    companion object {
        private const val RETRY_INTERVAL_MS: Long = 30_000
        private val MIN_AGE_BEFORE_RETRY: Duration = Duration.ofMinutes(5)
    }
}
