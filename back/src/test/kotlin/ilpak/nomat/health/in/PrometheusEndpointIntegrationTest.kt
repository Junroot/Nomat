package ilpak.nomat.health.`in`

import ilpak.nomat.infrastructure.integration.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient

// @SpringBootTest는 기본적으로 metrics export를 끄지만(management.defaults.metrics.export.enabled=false),
// @IntegrationTest 공용 컨텍스트에서 해당 property를 true로 켜므로 prometheus endpoint가 노출된다.
// (@AutoConfigureObservability를 쓰면 컨텍스트 캐시 키가 갈라져 ES 컨테이너가 중복 기동되므로 사용하지 않는다.)
@IntegrationTest
class PrometheusEndpointIntegrationTest(
    @Autowired private val webTestClient: WebTestClient,
) {

    @Test
    fun `prometheus_JVM과 HTTP 요청 메트릭을 exposition 포맷으로 노출한다`() {
        // http_server_requests 메트릭은 한 번이라도 요청이 처리된 뒤에야 등록되므로 먼저 호출한다
        webTestClient.get().uri("/health")
            .exchange()
            .expectStatus().isOk

        webTestClient.get().uri("/prometheus")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .value { body ->
                check("jvm_memory_used_bytes" in body) {
                    "prometheus 본문에 jvm_memory_used_bytes 메트릭이 없습니다"
                }
                check("http_server_requests" in body) {
                    "prometheus 본문에 http_server_requests 메트릭이 없습니다"
                }
                check("http_server_requests_seconds_bucket" !in body) {
                    "히스토그램 버킷이 비활성화되어야 하는데 http_server_requests_seconds_bucket 시계열이 존재합니다"
                }
            }
    }
}
