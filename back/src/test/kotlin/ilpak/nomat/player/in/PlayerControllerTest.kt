package ilpak.nomat.player.`in`

import ilpak.nomat.integration.IntegrationTest
import ilpak.nomat.player.application.PlayerService
import ilpak.nomat.player.application.domain.RegistrationType
import ilpak.nomat.player.application.dto.PlayerRequest
import ilpak.nomat.player.application.dto.PlayerResponse
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient

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
            .header("playerId", playerResponse.id.toString())
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(playerResponse.id)
            .jsonPath("$.nickname").isEqualTo(playerResponse.nickname)
            .jsonPath("$.registrationType").isEqualTo(playerResponse.registrationType.name)
            .jsonPath("$.registrationId").isEqualTo(playerResponse.registrationId)
    }
}
