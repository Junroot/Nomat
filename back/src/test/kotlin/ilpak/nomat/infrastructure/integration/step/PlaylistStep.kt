package ilpak.nomat.infrastructure.integration.step

import ilpak.nomat.infrastructure.integration.util.auth
import ilpak.nomat.player.application.dto.PlayerResponse
import ilpak.nomat.playlist.application.dto.PlaylistCreationRequest
import ilpak.nomat.playlist.application.dto.PlaylistCreationRequestTrack
import ilpak.nomat.playlist.application.dto.PlaylistResponse
import org.springframework.boot.test.context.TestComponent
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody

fun dummyPlaylistCreationRequest(
	title: String = "Test Playlist",
	description: String = "This is a test playlist.",
	tracks: List<PlaylistCreationRequestTrack> = listOf(
		PlaylistCreationRequestTrack(
			embedId = "testEmbedId",
			title = "Test Track",
			startTimeSec = 0,
			endTimeSec = 180,
			repeatCount = 1,
			additionalTitles = setOf("Test Track Alt"),
			isRepresentative = true
		)
	)
): PlaylistCreationRequest {
	return PlaylistCreationRequest(
		title = title,
		description = description,
		tracks = tracks
	)
}

@TestComponent
class PlaylistStep(
	private val client: WebTestClient,
) {

	fun save(playerResponse: PlayerResponse, request: PlaylistCreationRequest): PlaylistResponse {
		return client.post().uri("/playlists")
			.auth(playerResponse)
			.bodyValue(request)
			.exchange()
			.expectStatus().isCreated
			.expectBody<PlaylistResponse>()
			.returnResult()
			.responseBody ?: throw IllegalStateException("Failed to create playlist")
	}
}
