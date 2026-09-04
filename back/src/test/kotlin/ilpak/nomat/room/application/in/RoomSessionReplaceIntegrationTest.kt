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
import ilpak.nomat.room.application.dto.RoomDetailResponse
import ilpak.nomat.room.application.dto.RoomEventMessage
import ilpak.nomat.room.application.dto.RoomLeftEventMessage
import ilpak.nomat.room.application.dto.SessionReplacedEventMessage
import org.assertj.core.api.Assertions.assertThat
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
class RoomSessionReplaceIntegrationTest(
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
    fun `같은 방에 새 탭으로 접속하면 SESSION_REPLACED 이벤트가 전송된다`() {
        val room = roomStep.save(player, dummyRoomRequest(playlist.id))

        val sessionA = connectStomp(objectMapper, tokenService, port, player, room.id, "password")
        val receivedEvents = LinkedBlockingQueue<RoomEventMessage>()
        sessionA.subscribe("/topic/rooms/${room.id}", object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = RoomEventMessage::class.java
            override fun handleFrame(headers: StompHeaders, payload: Any?) {
                receivedEvents.add(payload as RoomEventMessage)
            }
        })

        // 같은 사용자가 같은 방에 새 탭으로 접속
        val sessionB = connectStomp(objectMapper, tokenService, port, player, room.id, "password")

        await()
            .pollInterval(Duration.ofMillis(100))
            .atMost(Duration.ofSeconds(5))
            .untilAsserted {
                val replacedEvents = receivedEvents.filter { it is SessionReplacedEventMessage }
                assertThat(replacedEvents).hasSize(1)
                val event = replacedEvents.first()
                assertThat(event.playerId).isEqualTo(player.id)
                assertThat(event.roomId).isEqualTo(room.id)
            }

        // 방에 여전히 1명만 있어야 함 (중복 입장 없음)
        val detail = getRoomDetail(room.id)
        assertThat(detail?.players).hasSize(1)

        sessionA.disconnect()
        sessionB.disconnect()
    }

    @Test
    fun `다른 방에 새 탭으로 접속하면 기존 방에서 퇴장하고 새 방에 입장한다`() {
        val roomA = roomStep.save(player, dummyRoomRequest(playlist.id))
        val roomB = roomStep.save(player, dummyRoomRequest(playlist.id, title = "Room B"))

        val observer = playerStep.save(dummyPlayerRequest(nickname = "observer", registrationId = "observerId"))
        val observerSession = connectStomp(objectMapper, tokenService, port, observer, roomA.id, "password")
        val receivedEvents = LinkedBlockingQueue<RoomEventMessage>()
        observerSession.subscribe("/topic/rooms/${roomA.id}", object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = RoomEventMessage::class.java
            override fun handleFrame(headers: StompHeaders, payload: Any?) {
                receivedEvents.add(payload as RoomEventMessage)
            }
        })

        // 플레이어가 방 A에 입장
        val sessionA = connectStomp(objectMapper, tokenService, port, player, roomA.id, "password")

        // 같은 플레이어가 방 B에 새 탭으로 접속
        val sessionB = connectStomp(objectMapper, tokenService, port, player, roomB.id, "password")

        await()
            .pollInterval(Duration.ofMillis(100))
            .atMost(Duration.ofSeconds(5))
            .untilAsserted {
                val leftEvents = receivedEvents.filter { it is RoomLeftEventMessage }
                assertThat(leftEvents).hasSize(1)
                assertThat(leftEvents.first().playerId).isEqualTo(player.id)

                val replacedEvents = receivedEvents.filter { it is SessionReplacedEventMessage }
                assertThat(replacedEvents).hasSize(1)
                assertThat(replacedEvents.first().playerId).isEqualTo(player.id)
            }

        // 방 B에 플레이어가 입장해 있어야 함
        val detailB = getRoomDetail(roomB.id, player)
        assertThat(detailB?.players?.map { it.id }).contains(player.id)

        observerSession.disconnect()
        sessionA.disconnect()
        sessionB.disconnect()
    }

    @Test
    fun `교체된 세션의 disconnect 시 유예 기간이 스킵된다`() {
        val room = roomStep.save(player, dummyRoomRequest(playlist.id))

        val observer = playerStep.save(dummyPlayerRequest(nickname = "observer", registrationId = "observerId"))
        val observerSession = connectStomp(objectMapper, tokenService, port, observer, room.id, "password")
        val receivedEvents = LinkedBlockingQueue<RoomEventMessage>()
        observerSession.subscribe("/topic/rooms/${room.id}", object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = RoomEventMessage::class.java
            override fun handleFrame(headers: StompHeaders, payload: Any?) {
                receivedEvents.add(payload as RoomEventMessage)
            }
        })

        // 플레이어가 방에 입장 (세션 A)
        val sessionA = connectStomp(objectMapper, tokenService, port, player, room.id, "password")

        // 같은 플레이어가 새 탭으로 접속 (세션 B) → 세션 A는 교체됨
        val sessionB = connectStomp(objectMapper, tokenService, port, player, room.id, "password")

        // 교체된 세션 A를 disconnect
        sessionA.disconnect()

        // 유예 시간(2초) + sweeper 폴링 주기(1초) + 여유가 지나도 퇴장 이벤트가 발생하지 않아야 함
        Thread.sleep(4000)
        val leftEvents = receivedEvents.filter { it is RoomLeftEventMessage }
        assertThat(leftEvents).isEmpty()

        // 방에 플레이어가 여전히 있어야 함
        val detail = getRoomDetail(room.id)
        assertThat(detail?.players?.map { it.id }).contains(player.id)

        observerSession.disconnect()
        sessionB.disconnect()
    }

    private fun getRoomDetail(roomId: Long, auth: PlayerResponse = player): RoomDetailResponse? {
        return client.get().uri("/rooms/{roomId}", roomId)
            .auth(auth)
            .exchange()
            .expectStatus().isOk()
            .expectBody<RoomDetailResponse>()
            .returnResult()
            .responseBody
    }
}
