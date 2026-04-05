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
import ilpak.nomat.player.application.dto.PlayerResponse
import ilpak.nomat.playlist.application.dto.PlaylistWithTrackResponse
import ilpak.nomat.room.application.dto.RoomChatRequest
import ilpak.nomat.room.application.dto.RoomEventMessage
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
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import java.lang.reflect.Type
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@IntegrationTest
class RoomStompChatIntegrationTest(
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
    fun `채팅 메시지를 전송하면 구독자에게 CHAT 이벤트가 전달된다`() {
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

        Thread.sleep(500)

        sessionB.send("/app/rooms/chat", RoomChatRequest(content = "안녕하세요"))

        await()
            .pollInterval(Duration.ofMillis(100))
            .atMost(Duration.ofSeconds(5))
            .untilAsserted {
                val chatEvents = receivedEvents.filter { it is ilpak.nomat.room.application.dto.RoomChatEventMessage }
                assertThat(chatEvents).hasSize(1)
                val event = chatEvents.first() as ilpak.nomat.room.application.dto.RoomChatEventMessage
                assertThat(event.playerId).isEqualTo(joiner.id)
                assertThat(event.nickname).isEqualTo("joiner")
                assertThat(event.roomId).isEqualTo(room.id)
                assertThat(event.content).isEqualTo("안녕하세요")
                assertThat(event.timestamp).isNotNull()
            }

        sessionA.disconnect()
        sessionB.disconnect()
    }

    @Test
    fun `자신이 보낸 채팅 메시지도 구독자에게 전달된다`() {
        val room = roomStep.save(player, dummyRoomRequest(playlist.id))

        val session = connectStomp(player, room.id, "password")

        val receivedEvents = LinkedBlockingQueue<RoomEventMessage>()
        session.subscribe("/topic/rooms/${room.id}", object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = RoomEventMessage::class.java
            override fun handleFrame(headers: StompHeaders, payload: Any?) {
                receivedEvents.add(payload as RoomEventMessage)
            }
        })

        Thread.sleep(500)

        session.send("/app/rooms/chat", RoomChatRequest(content = "테스트 메시지"))

        await()
            .pollInterval(Duration.ofMillis(100))
            .atMost(Duration.ofSeconds(5))
            .untilAsserted {
                val chatEvents = receivedEvents.filter { it is ilpak.nomat.room.application.dto.RoomChatEventMessage }
                assertThat(chatEvents).hasSize(1)
                val event = chatEvents.first() as ilpak.nomat.room.application.dto.RoomChatEventMessage
                assertThat(event.playerId).isEqualTo(player.id)
                assertThat(event.content).isEqualTo("테스트 메시지")
            }

        session.disconnect()
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
