package ilpak.nomat.favoriteplaylist.`in`

import ilpak.nomat.favoriteplaylist.application.domain.FavoritePlaylistRepository
import ilpak.nomat.infrastructure.integration.IntegrationTest
import ilpak.nomat.infrastructure.integration.step.FavoritePlaylistStep
import ilpak.nomat.infrastructure.integration.step.PlayerStep
import ilpak.nomat.infrastructure.integration.step.PlaylistStep
import ilpak.nomat.infrastructure.integration.step.dummyPlayerRequest
import ilpak.nomat.infrastructure.integration.step.dummyPlaylistCreationRequest
import ilpak.nomat.infrastructure.integration.util.auth
import ilpak.nomat.player.application.dto.PlayerResponse
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Duration

@IntegrationTest
class PlaylistDeletedHandlerTest(
    @Autowired private val client: WebTestClient,
    @Autowired private val playerStep: PlayerStep,
    @Autowired private val playlistStep: PlaylistStep,
    @Autowired private val favoritePlaylistStep: FavoritePlaylistStep,
    @Autowired private val favoritePlaylistRepository: FavoritePlaylistRepository,
) {
    private lateinit var owner: PlayerResponse
    private lateinit var fan: PlayerResponse

    @BeforeEach
    fun setUp() {
        owner = playerStep.save(dummyPlayerRequest(nickname = "owner", registrationId = "owner-id"))
        fan = playerStep.save(dummyPlayerRequest(nickname = "fan", registrationId = "fan-id"))
    }

    @Test
    fun `playlist 삭제 시 favorite row 정리`() {
        val playlist = playlistStep.save(owner, dummyPlaylistCreationRequest(title = "관심 플리"))
        favoritePlaylistStep.save(fan, playlist.id)

        assertThat(favoritePlaylistRepository.findByPlayerId(fan.id)).hasSize(1)

        client.delete().uri("/playlists/${playlist.id}")
            .auth(owner)
            .exchange()
            .expectStatus().isOk

        await()
            .atMost(Duration.ofSeconds(SYNC_TIMEOUT_SECONDS))
            .untilAsserted {
                assertThat(favoritePlaylistRepository.findByPlayerId(fan.id)).isEmpty()
            }
    }

    companion object {
        private const val SYNC_TIMEOUT_SECONDS = 10L
    }
}
