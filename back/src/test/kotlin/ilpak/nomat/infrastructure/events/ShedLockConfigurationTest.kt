package ilpak.nomat.infrastructure.events

import ilpak.nomat.infrastructure.integration.IntegrationTest
import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.core.LockProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.Instant

@IntegrationTest
class ShedLockConfigurationTest(
    @Autowired private val lockProvider: LockProvider,
) {

    @Test
    fun `동일 lock 이름은 한 번만 획득 가능하며 해제 후 재획득 가능`() {
        val configuration = LockConfiguration(
            Instant.now(),
            "shedlock-integration-test",
            Duration.ofMinutes(1),
            Duration.ZERO,
        )

        val firstAcquisition = lockProvider.lock(configuration)
        val secondAcquisition = lockProvider.lock(configuration)

        assertThat(firstAcquisition).isPresent
        assertThat(secondAcquisition).isEmpty

        firstAcquisition.get().unlock()

        val thirdAcquisition = lockProvider.lock(configuration)
        assertThat(thirdAcquisition).isPresent
        thirdAcquisition.get().unlock()
    }
}
