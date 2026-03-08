package ilpak.nomat.room.application.`in`

import com.fasterxml.jackson.databind.ObjectMapper
import ilpak.nomat.auth.application.TokenService
import ilpak.nomat.infrastructure.integration.IntegrationTest
import ilpak.nomat.infrastructure.integration.step.PlayerStep
import ilpak.nomat.infrastructure.integration.step.PlaylistStep
import ilpak.nomat.infrastructure.integration.step.RoomStep
import ilpak.nomat.infrastructure.integration.step.dummyPlayerRequest
import ilpak.nomat.infrastructure.integration.step.dummyPlaylistCreationRequest
import ilpak.nomat.infrastructure.integration.step.dummyRoomRequest
import ilpak.nomat.infrastructure.integration.util.auth
import ilpak.nomat.player.application.dto.PlayerResponse
import ilpak.nomat.playlist.application.dto.PlaylistWithTrackResponse
import ilpak.nomat.room.application.dto.RoomDetailResponse
import ilpak.nomat.room.application.dto.RoomEventMessage
import ilpak.nomat.room.application.dto.RoomLeftEventMessage
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import java.lang.reflect.Type
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@IntegrationTest
class RoomLeaveIntegrationTest(
    @Autowired private val client: WebTestClient,
    @Autowired private val playerStep: PlayerStep,
    @Autowired private val playlistStep: PlaylistStep,
    @Autowired private val roomStep: RoomStep,
    @Autowired private val tokenService: TokenService,
    @Autowired private val objectMapper: ObjectMapper,
    @LocalServerPort private val port: Int,
) {

    private lateinit var player: PlayerResponse
    private lateinit var playlist: PlaylistWithTrackResponse

    @BeforeEach
    fun setUp() {
        player = playerStep.save(dummyPlayerRequest())
        playlist = playlistStep.save(player, dummyPlaylistCreationRequest())
    }

    @Test
    fun `연결 해제 후 유예 시간 내 재접속하면 퇴장 처리되지 않는다`() {
        val room = roomStep.save(player, dummyRoomRequest(playlist.id))
        val joiner = playerStep.save(dummyPlayerRequest(nickname = "joiner", registrationId = "joinerId"))

        val sessionA = connectStomp(player, room.id, "password")
        val sessionB = connectStomp(joiner, room.id, "password")

        val receivedEvents = LinkedBlockingQueue<RoomEventMessage>()
        sessionA.subscribe("/topic/rooms/${room.id}", object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = RoomEventMessage::class.java
            override fun handleFrame(headers: StompHeaders, payload: Any?) {
                receivedEvents.add(payload as RoomEventMessage)
            }
        })

        // 유저 B 연결 해제
        sessionB.disconnect()
        Thread.sleep(500)

        // 유예 시간(2초) 내 재접속
        val sessionB2 = connectStomp(joiner, room.id, "password")

        // 유예 시간(2초)이 지나도 퇴장 이벤트가 발생하지 않아야 함
        Thread.sleep(3000)
        val leftEvents = receivedEvents.filter { it is RoomLeftEventMessage }
        assertThat(leftEvents).isEmpty()

        val detail = getRoomDetail(room.id)
        assertThat(detail?.players).hasSize(2)
        assertThat(detail?.players?.map { it.id }).contains(joiner.id)

        sessionA.disconnect()
        sessionB2.disconnect()
    }

    @Test
    fun `연결 해제 후 유예 시간 경과 시 퇴장 메시지가 전달된다`() {
        val room = roomStep.save(player, dummyRoomRequest(playlist.id))
        val joiner = playerStep.save(dummyPlayerRequest(nickname = "joiner", registrationId = "joinerId"))

        val sessionA = connectStomp(player, room.id, "password")
        val sessionB = connectStomp(joiner, room.id, "password")

        val receivedEvents = LinkedBlockingQueue<RoomEventMessage>()
        sessionA.subscribe("/topic/rooms/${room.id}", object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = RoomEventMessage::class.java
            override fun handleFrame(headers: StompHeaders, payload: Any?) {
                receivedEvents.add(payload as RoomEventMessage)
            }
        })

        // 유저 B 연결 해제 → 유예 시간(2초) 후 퇴장 처리
        sessionB.disconnect()

        await()
            .pollInterval(Duration.ofMillis(500))
            .atMost(Duration.ofSeconds(10))
            .untilAsserted {
                val leftEvents = receivedEvents.filter { it is RoomLeftEventMessage }
                assertThat(leftEvents).hasSize(1)
                val event = leftEvents.first()
                assertThat(event.playerId).isEqualTo(joiner.id)
                assertThat(event.nickname).isEqualTo("joiner")
                assertThat(event.roomId).isEqualTo(room.id)
            }

        sessionA.disconnect()
    }

    @Test
    fun `연결 해제 후 유예 시간 경과 시 방의 entries에서 해당 유저가 제거된다`() {
        val room = roomStep.save(player, dummyRoomRequest(playlist.id))
        val joiner = playerStep.save(dummyPlayerRequest(nickname = "joiner", registrationId = "joinerId"))

        connectStomp(player, room.id, "password")
        val sessionB = connectStomp(joiner, room.id, "password")

        // 유저 B 연결 해제
        sessionB.disconnect()

        // 유예 시간(2초) 경과 후 entries에서 제거 확인
        await()
            .pollInterval(Duration.ofMillis(500))
            .atMost(Duration.ofSeconds(10))
            .untilAsserted {
                val detail = getRoomDetail(room.id)
                assertThat(detail?.players?.map { it.id }).doesNotContain(joiner.id)
            }
    }

    @Test
    fun `모든 유저가 퇴장하면 방이 삭제된다`() {
        val room = roomStep.save(player, dummyRoomRequest(playlist.id))

        val sessionA = connectStomp(player, room.id, "password")

        // 유일한 유저가 연결 해제
        sessionA.disconnect()

        // 유예 시간(2초) 경과 후 방 삭제 확인
        await()
            .pollInterval(Duration.ofMillis(500))
            .atMost(Duration.ofSeconds(10))
            .untilAsserted {
                client.get().uri("/rooms/{roomId}", room.id)
                    .auth(player)
                    .exchange()
                    .expectStatus().isNotFound()
            }
    }

    private fun getRoomDetail(roomId: Long): RoomDetailResponse? {
        return client.get().uri("/rooms/{roomId}", roomId)
            .auth(player)
            .exchange()
            .expectStatus().isOk()
            .expectBody<RoomDetailResponse>()
            .returnResult()
            .responseBody
    }

    private fun connectStomp(player: PlayerResponse, roomId: Long, password: String?): StompSession {
        val stompClient = WebSocketStompClient(StandardWebSocketClient())
        stompClient.messageConverter = MappingJackson2MessageConverter(objectMapper)

        val stompHeaders = StompHeaders()
        stompHeaders.add("roomId", roomId.toString())
        if (password != null) {
            stompHeaders.add("password", password)
        }

        val httpHeaders = WebSocketHttpHeaders()
        val token = tokenService.getNewToken(player.id)
        httpHeaders.add("Cookie", "${TokenService.TOKEN_COOKIE_KEY}=$token")

        return stompClient.connectAsync(
            "ws://localhost:$port/ws",
            httpHeaders,
            stompHeaders,
            object : StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS)
    }
}
