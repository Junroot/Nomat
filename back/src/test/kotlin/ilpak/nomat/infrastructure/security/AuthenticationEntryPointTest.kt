package ilpak.nomat.infrastructure.security

import ilpak.nomat.infrastructure.integration.IntegrationTest
import ilpak.nomat.infrastructure.integration.step.PlayerStep
import ilpak.nomat.infrastructure.integration.step.PlaylistStep
import ilpak.nomat.infrastructure.integration.step.dummyPlayerRequest
import ilpak.nomat.infrastructure.integration.step.dummyPlaylistCreationRequest
import ilpak.nomat.infrastructure.integration.util.auth
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * 미인증(401)과 권한 없음(403)의 구분을 계약으로 고정한다.
 *
 * 엔트리포인트를 교체하지 않고 지우면 기본값이 인가 엔드포인트로 302를 뱉어
 * 브라우저가 CORS로 막히고 클라이언트가 상태 코드를 읽을 수 없게 된다.
 * `WWW-Authenticate` 헤더가 붙으면 브라우저 기본 인증 대화상자가 뜬다.
 * 두 실패 모드를 아래 단언이 막는다.
 */
@IntegrationTest
class AuthenticationEntryPointTest(
    @Autowired private val client: WebTestClient,
    @Autowired private val playerStep: PlayerStep,
    @Autowired private val playlistStep: PlaylistStep,
) {

    @Test
    fun `미인증 요청은 401이며 리다이렉트도 인증 챌린지도 아니다`() {
        val result = client.get().uri("/players/me")
            .exchange()
            .expectStatus().isUnauthorized
            .expectHeader().doesNotExist("WWW-Authenticate")
            .expectBody()
            .returnResult()

        assertThat(result.status.is3xxRedirection).isFalse()
    }

    @Test
    fun `유효하지 않은 자격 증명도 401이다`() {
        client.get().uri("/rooms")
            .header("playerId", "not-a-player-id")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `정적 리소스 허용 경로는 무인증으로도 401이 아니다`() {
        client.get().uri("/html/close.html")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `로그인 허용 경로는 무인증으로도 401이 아니다`() {
        val result = client.get().uri("/login")
            .exchange()
            .expectBody()
            .returnResult()

        assertThat(result.status).isNotEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `actuator 엔드포인트는 무인증으로도 401이 아니다`() {
        client.get().uri("/health")
            .exchange()
            .expectStatus().isOk
    }

    /**
     * 401과 달리 비즈니스 403은 거부 사유를 본문에 담는다. 프론트는 이 문구를 그대로
     * 토스트로 띄우므로(인터셉터가 403에서 페이지를 날리지 않게 된 뒤부터 비로소 보인다)
     * 상태 코드만이 아니라 본문까지 계약으로 고정한다.
     */
    @Test
    fun `남의 플레이리스트를 수정하면 403과 거부 사유가 함께 온다`() {
        val owner = playerStep.save(dummyPlayerRequest())
        val other = playerStep.save(
            dummyPlayerRequest(nickname = "other", registrationId = "otherRegistrationId")
        )
        val playlist = playlistStep.save(owner, dummyPlaylistCreationRequest())

        client.put().uri("/playlists/{playlistId}", playlist.id)
            .auth(other)
            .bodyValue(dummyPlaylistCreationRequest())
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.message").isNotEmpty
    }
}
