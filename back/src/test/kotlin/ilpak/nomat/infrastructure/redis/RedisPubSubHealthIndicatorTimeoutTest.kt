package ilpak.nomat.infrastructure.redis

import ilpak.nomat.infrastructure.integration.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.actuate.health.Status
import org.springframework.test.context.TestPropertySource

@IntegrationTest
@TestPropertySource(properties = ["app.health.pubsub.timeout-ms=0"])
class RedisPubSubHealthIndicatorTimeoutTest(
    @Autowired @Qualifier("redisPubSubHealthIndicator")
    private val healthIndicator: HealthIndicator,
) {

    @Test
    fun `health_timeout 안에 round-trip이 끝나지 않으면 DOWN을 반환한다`() {
        val health = healthIndicator.health()

        assertThat(health.status).isEqualTo(Status.DOWN)
        assertThat(health.details).containsEntry("timeoutMs", 0L)
        assertThat(health.details).containsEntry("reason", "expected ping not received")
        assertThat(health.details).containsKey("channel")
    }
}
