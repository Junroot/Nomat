package ilpak.nomat.infrastructure.integration.step

import ilpak.nomat.favoriteplaylist.application.dto.FavoritePlaylistRequest
import ilpak.nomat.infrastructure.integration.util.auth
import ilpak.nomat.player.application.dto.PlayerResponse
import org.springframework.boot.test.context.TestComponent
import org.springframework.test.web.reactive.server.WebTestClient

@TestComponent
class FavoritePlaylistStep(
    private val client: WebTestClient,
) {

    fun save(playerResponse: PlayerResponse, playlistId: Long) {
        client.post().uri("/favorite-playlists")
            .auth(playerResponse)
            .bodyValue(FavoritePlaylistRequest(playlistId))
            .exchange()
            .expectStatus().isCreated
    }

    fun delete(playerResponse: PlayerResponse, playlistId: Long) {
        client.delete().uri("/favorite-playlists/{playlistId}", playlistId)
            .auth(playerResponse)
            .exchange()
            .expectStatus().isOk
    }
}
