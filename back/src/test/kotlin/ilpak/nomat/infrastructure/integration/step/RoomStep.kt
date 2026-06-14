package ilpak.nomat.infrastructure.integration.step

import ilpak.nomat.infrastructure.integration.util.auth
import ilpak.nomat.player.application.dto.PlayerResponse
import ilpak.nomat.room.application.RoomService
import ilpak.nomat.room.application.dto.RoomDetailResponse
import ilpak.nomat.room.application.dto.RoomRequest
import org.springframework.boot.test.context.TestComponent
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody

fun dummyRoomRequest(
    playlistId: Long,
    title: String = "Test Room",
    password: String = "password",
    maxEntriesCount: Int = 10,
): RoomRequest = RoomRequest(
    title = title,
    password = password,
    maxEntriesCount = maxEntriesCount,
    playlistId = playlistId,
)

@TestComponent
class RoomStep(
    private val client: WebTestClient,
    private val roomService: RoomService,
) {

    fun join(playerId: Long, roomId: Long, password: String?) {
        roomService.join(roomId, playerId, password)
    }

    fun start(playerId: Long, roomId: Long) {
        roomService.start(roomId, playerId)
    }

    fun save(playerResponse: PlayerResponse, request: RoomRequest): RoomDetailResponse {
        return client.post().uri("/rooms")
            .auth(playerResponse)
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated
            .expectBody<RoomDetailResponse>()
            .returnResult()
            .responseBody ?: throw IllegalStateException("Failed to create room")
    }
}
