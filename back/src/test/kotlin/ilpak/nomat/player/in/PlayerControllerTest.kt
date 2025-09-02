package ilpak.nomat.player.`in`

import ilpak.nomat.infrastructure.integration.IntegrationTest
import ilpak.nomat.infrastructure.integration.util.auth
import ilpak.nomat.player.application.PlayerService
import ilpak.nomat.player.application.domain.RegistrationType
import ilpak.nomat.player.application.dto.PlayerNicknameRequest
import ilpak.nomat.player.application.dto.PlayerRequest
import ilpak.nomat.player.application.dto.PlayerResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody

@IntegrationTest
class PlayerControllerTest(
	@Autowired val client: WebTestClient,
	@Autowired val playerService: PlayerService,
) {

	private lateinit var playerResponse: PlayerResponse

	@BeforeEach
	fun setUp() {
		playerResponse = playerService.save(
			PlayerRequest(
				nickname = "ROOT#3465",
				registrationType = RegistrationType.DISCORD,
				registrationId = "abc"
			)
		)
	}

	@Test
	fun getMe() {
		client.get().uri("/players/me")
			.auth(playerResponse)
			.exchange()
			.expectStatus().isOk()
			.expectBody<PlayerResponse>()
			.value { assertThat(it).isEqualTo(playerResponse) }
	}

	@Test
	fun updateNickname() {
		val newNickname = "NewNickname"

		client.put().uri("/players/me/nickname")
			.auth(playerResponse)
			.bodyValue(PlayerNicknameRequest(newNickname))
			.exchange()
			.expectStatus().isOk()

		client.get().uri("/players/me")
			.auth(playerResponse)
			.exchange()
			.expectStatus().isOk()
			.expectBody<PlayerResponse>()
			.value { response ->
				assertThat(response.nickname).isEqualTo(newNickname)
			}
	}
}
