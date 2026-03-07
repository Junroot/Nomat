package ilpak.nomat.room.application.`in`

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
import ilpak.nomat.room.application.dto.RoomJoinedEventMessage
import ilpak.nomat.room.application.dto.RoomRequest
import ilpak.nomat.room.application.dto.RoomResponse
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotNull

@IntegrationTest
class RoomJoinIntegrationTest(
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
    fun `STOMP 연결 시 방에 입장한다`() {
        val room = roomStep.save(player, dummyRoomRequest(playlist.id))

        val session = connectStomp(player, room.id, "password")

        val detail = getRoomDetail(room.id)
        assertNotNull(detail)
        assertThat(detail.players).hasSize(1)
        assertThat(detail.players[0].id).isEqualTo(player.id)
        session.disconnect()
    }

    @Test
    fun `잘못된 비밀번호로 STOMP 연결 시 입장이 거부된다`() {
        val room = roomStep.save(player, dummyRoomRequest(playlist.id))
        val joiner = playerStep.save(dummyPlayerRequest(nickname = "joiner", registrationId = "joinerId"))

        assertThatThrownBy {
            connectStomp(joiner, room.id, "wrong_password")
        }
    }

    @Test
    fun `비밀번호가 없는 방에 입장한다`() {
        val room = roomStep.save(
            player,
            RoomRequest(title = "No Password Room", password = null, maxEntriesCount = 10, playlistId = playlist.id)
        )

        val session = connectStomp(player, room.id, null)

        val detail = getRoomDetail(room.id)
        assertNotNull(detail)
        assertThat(detail.players).hasSize(1)
        session.disconnect()
    }

    @Test
    fun `PENDING 방에 첫 입장 후 ACTIVE로 전환된다`() {
        val room = roomStep.save(player, dummyRoomRequest(playlist.id))

        val session = connectStomp(player, room.id, "password")

        client.get().uri("/rooms")
            .auth(player)
            .exchange()
            .expectStatus().isOk()
            .expectBody<List<RoomResponse>>()
            .value { rooms ->
                assertThat(rooms.map { it.id }).contains(room.id)
            }
        session.disconnect()
    }

    @Test
    fun `정원 초과 시 동시 입장 시도 시 정확히 N명만 성공한다`() {
        val room = roomStep.save(player, dummyRoomRequest(playlist.id, maxEntriesCount = 2))
        val players = (1..3).map {
            playerStep.save(dummyPlayerRequest(nickname = "player$it", registrationId = "reg$it"))
        }

        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(3)
        val results = ConcurrentHashMap<Long, Boolean>()
        val executor = Executors.newFixedThreadPool(3)

        players.forEach { p ->
            executor.submit {
                startLatch.await()
                try {
                    connectStomp(p, room.id, "password")
                    results[p.id] = true
                } catch (e: Exception) {
                    results[p.id] = false
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        doneLatch.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        assertThat(results.values.count { it }).isEqualTo(2)
        assertThat(results.values.count { !it }).isEqualTo(1)
    }

    @Test
    fun `방에 입장하면 기존 유저에게 입장 이벤트가 전달된다`() {
        val room = roomStep.save(player, dummyRoomRequest(playlist.id))
        val joiner = playerStep.save(dummyPlayerRequest(nickname = "joiner", registrationId = "joinerId"))

        val sessionA = connectStomp(player, room.id, "password")
        val receivedEvents = LinkedBlockingQueue<RoomJoinedEventMessage>()
        sessionA.subscribe("/topic/rooms/${room.id}", object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = RoomJoinedEventMessage::class.java
            override fun handleFrame(headers: StompHeaders, payload: Any?) {
                receivedEvents.add(payload as RoomJoinedEventMessage)
            }
        })

        val sessionB = connectStomp(joiner, room.id, "password")

        await()
            .pollInterval(Duration.ofMillis(100))
            .atMost(Duration.ofSeconds(5))
            .untilAsserted {
                val event = receivedEvents.peek()
                assertThat(event).isNotNull()
                assertThat(event.playerId).isEqualTo(joiner.id)
                assertThat(event.nickname).isEqualTo("joiner")
                assertThat(event.roomId).isEqualTo(room.id)
            }

        sessionA.disconnect()
        sessionB.disconnect()
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
