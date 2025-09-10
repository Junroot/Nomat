package ilpak.nomat.infrastructure.integration.util

import ilpak.nomat.player.application.dto.PlayerResponse
import org.springframework.test.web.reactive.server.WebTestClient

fun WebTestClient.RequestHeadersSpec<*>.auth(playerResponse: PlayerResponse): WebTestClient.RequestHeadersSpec<*> {
    return header("playerId", playerResponse.id.toString())
}

fun WebTestClient.RequestBodySpec.auth(playerResponse: PlayerResponse): WebTestClient.RequestBodySpec {
    return header("playerId", playerResponse.id.toString())
}
