package ilpak.nomat.infrastructure.events.fixture

import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicInteger

data class FailingTestEvent(val id: String)

@Component
class FailingTestHandler {
    val attemptCount: AtomicInteger = AtomicInteger(0)

    @Volatile
    var shouldFail: Boolean = true

    fun reset() {
        attemptCount.set(0)
        shouldFail = true
    }

    @ApplicationModuleListener(id = "failing-test-handler")
    fun handle(@Suppress("UNUSED_PARAMETER") event: FailingTestEvent) {
        attemptCount.incrementAndGet()
        if (shouldFail) {
            error("intentional failure for testing")
        }
    }
}
