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
    fun `health_익명 호출 시 200과 컴포넌트 status는 노출하되 details는 숨긴다`() {
        webTestClient.get().uri("/health")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
            .jsonPath("$.components.redisPubSub.status").isEqualTo("UP")
            .jsonPath("$.components.redisPubSub.details").doesNotExist()
    }
}
