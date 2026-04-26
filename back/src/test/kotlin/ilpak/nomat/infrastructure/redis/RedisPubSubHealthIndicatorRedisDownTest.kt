package ilpak.nomat.infrastructure.redis

import com.redis.testcontainers.RedisContainer
import ilpak.nomat.infrastructure.integration.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.actuate.health.Status
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.reactive.server.WebTestClient

@IntegrationTest
class RedisPubSubHealthIndicatorRedisDownTest(
    @Autowired @Qualifier("redisPubSubHealthIndicator")
    private val healthIndicator: HealthIndicator,
    @Autowired private val redisContainer: RedisContainer,
    @Autowired private val webTestClient: WebTestClient,
) {

    @Test
    fun `health_Redis 정지 시 DOWN을 반환하고 health endpoint는 503을 반환한다`() {
        redisContainer.stop()

        val health = healthIndicator.health()
        assertThat(health.status).isEqualTo(Status.DOWN)

        webTestClient.get().uri("/health")
            .exchange()
            .expectStatus().isEqualTo(503)
            .expectBody()
            .jsonPath("$.status").isEqualTo("DOWN")
            .jsonPath("$.components.redisPubSub.status").isEqualTo("DOWN")
    }
}
