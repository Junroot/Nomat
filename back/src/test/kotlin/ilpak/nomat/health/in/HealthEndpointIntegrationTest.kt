package ilpak.nomat.health.`in`

import ilpak.nomat.infrastructure.integration.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient

@IntegrationTest
class HealthEndpointIntegrationTest(
    @Autowired private val webTestClient: WebTestClient,
) {

    @Test
    fun `health_정상 시 200과 redisPubSub 컴포넌트 상태를 노출한다`() {
        webTestClient.get().uri("/health")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
            .jsonPath("$.components.redisPubSub.status").isEqualTo("UP")
            .jsonPath("$.components.redisPubSub.details.channel").exists()
            .jsonPath("$.components.redisPubSub.details.latencyMs").exists()
    }
}
