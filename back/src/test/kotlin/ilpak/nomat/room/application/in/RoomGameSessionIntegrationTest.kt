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
import ilpak.nomat.infrastructure.integration.util.connectStomp
import ilpak.nomat.player.application.dto.PlayerResponse
import ilpak.nomat.playlist.application.dto.PlaylistWithTrackResponse
import ilpak.nomat.room.application.dto.GameStartedEventMessage
import ilpak.nomat.room.application.dto.RoomDetailResponse
import ilpak.nomat.room.application.dto.RoomEventMessage
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import java.lang.reflect.Type
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue

@IntegrationTest
class RoomGameSessionIntegrationTest(
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
    fun `방장이 게임을 시작하면 구독자에게 STARTED 이벤트가 전달된다`() {
        val room = roomStep.save(player, dummyRoomRequest(playlist.id))
        val joiner = playerStep.save(dummyPlayerRequest(nickname = "joiner", registrationId = "joinerId"))

        val sessionA = connectStomp(objectMapper, tokenService, port, player, room.id, "password")
        val sessionB = connectStomp(objectMapper, tokenService, port, joiner, room.id, "password")

        val receivedEvents = LinkedBlockingQueue<RoomEventMessage>()
        sessionB.subscribe("/topic/rooms/${room.id}", object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = RoomEventMessage::class.java
            override fun handleFrame(headers: StompHeaders, payload: Any?) {
                receivedEvents.add(payload as RoomEventMessage)
            }
        })

        Thread.sleep(500)

        sessionA.send("/app/rooms/start", emptyMap<String, Any>())

        await()
            .pollInterval(Duration.ofMillis(100))
            .atMost(Duration.ofSeconds(5))
            .untilAsserted {
                val startedEvents = receivedEvents.filterIsInstance<GameStartedEventMessage>()
                assertThat(startedEvents).hasSize(1)
                val event = startedEvents.first()
                assertThat(event.playerId).isEqualTo(player.id)
                assertThat(event.nickname).isEqualTo(player.nickname)
                assertThat(event.roomId).isEqualTo(room.id)
            }

        sessionA.disconnect()
        sessionB.disconnect()
    }

    @Test
    fun `게임 중에는 멤버가 아닌 플레이어의 입장이 거부된다`() {
        val room = roomStep.save(player, dummyRoomRequest(playlist.id))
        val newcomer = playerStep.save(dummyPlayerRequest(nickname = "newcomer", registrationId = "newcomerId"))

        val sessionA = connectStomp(objectMapper, tokenService, port, player, room.id, "password")
        roomStep.start(player.id, room.id)

        assertThatThrownBy {
            connectStomp(objectMapper, tokenService, port, newcomer, room.id, "password")
        }

        val detail = getRoomDetail(room.id)
        assertThat(detail?.players?.map { it.id }).doesNotContain(newcomer.id)

        sessionA.disconnect()
    }

    @Test
    fun `게임 중 끊긴 기존 멤버는 유예 시간 내 재접속할 수 있다`() {
        val room = roomStep.save(player, dummyRoomRequest(playlist.id))
        val joiner = playerStep.save(dummyPlayerRequest(nickname = "joiner", registrationId = "joinerId"))

        val sessionA = connectStomp(objectMapper, tokenService, port, player, room.id, "password")
        val sessionB = connectStomp(objectMapper, tokenService, port, joiner, room.id, "password")
        roomStep.start(player.id, room.id)

        // 게임 중 joiner 연결 해제
        sessionB.disconnect()
        Thread.sleep(500)

        // 유예 시간 내 재접속 — PLAYING 상태여도 거부되지 않아야 함
        val sessionB2 = connectStomp(objectMapper, tokenService, port, joiner, room.id, "password")

        val detail = getRoomDetail(room.id)
        assertThat(detail?.players?.map { it.id }).contains(joiner.id)

        sessionA.disconnect()
        sessionB2.disconnect()
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
}
