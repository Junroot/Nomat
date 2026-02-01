package ilpak.nomat.infrastructure.integration.step

import ilpak.nomat.infrastructure.integration.util.auth
import ilpak.nomat.player.application.dto.PlayerResponse
import ilpak.nomat.room.application.dto.RoomDetailResponse
import ilpak.nomat.room.application.dto.RoomJoinRequest
import ilpak.nomat.room.application.dto.RoomRequest
import org.springframework.boot.test.context.TestComponent
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpHeaders
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import java.lang.reflect.Type
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.messaging.WebSocketStompClient
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
    private val webSocketClient: WebSocketStompClient,
    @LocalServerPort private val port: Int,
) {

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

    fun join(playerResponse: PlayerResponse, roomId: Long, roomJoinRequest: RoomJoinRequest): CompletableFuture<StompSession> {
        val joinCompleteLatch = CountDownLatch(1)

        val future = webSocketClient.connectAsync(
            "ws://localhost:{port}/ws",
            WebSocketHttpHeaders(HttpHeaders().apply { put("playerId", listOf(playerResponse.id.toString())) }),
            object : StompSessionHandlerAdapter() {
                override fun afterConnected(session: StompSession, connectedHeaders: StompHeaders) {
                    // 응답 메시지를 구독하여 join 완료 대기
                    session.subscribe("/topic/rooms.$roomId.joined", object : StompFrameHandler {
                        override fun getPayloadType(headers: StompHeaders): Type = Map::class.java

                        override fun handleFrame(headers: StompHeaders, payload: Any?) {
                            joinCompleteLatch.countDown()
                        }
                    })

                    // join 메시지 전송
                    session.send("/app/rooms.$roomId.join", roomJoinRequest)
                }
            },
            port,
        )

        // WebSocket 연결이 완료될 때까지 대기
        future.get(10, TimeUnit.SECONDS)

        // join 응답이 올 때까지 대기 (최대 10초)
        if (!joinCompleteLatch.await(10, TimeUnit.SECONDS)) {
            throw IllegalStateException("Join response timeout")
        }

        return future
    }
}
