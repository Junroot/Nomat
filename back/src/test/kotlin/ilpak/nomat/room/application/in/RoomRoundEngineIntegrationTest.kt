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
import ilpak.nomat.room.application.dto.GameEndedEventMessage
import ilpak.nomat.room.application.dto.RoomChatEventMessage
import ilpak.nomat.room.application.dto.RoomChatRequest
import ilpak.nomat.room.application.dto.RoomDetailResponse
import ilpak.nomat.room.application.dto.RoomEventMessage
import ilpak.nomat.room.application.dto.RoomJoinedEventMessage
import ilpak.nomat.room.application.dto.RoundRevealedEventMessage
import ilpak.nomat.room.application.dto.RoundStartedEventMessage
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import java.lang.reflect.Type
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue

private const val TRACK_TITLE = "Test Track"
private const val TRACK_EMBED_ID = "testEmbedId"

@IntegrationTest
class RoomRoundEngineIntegrationTest(
    @Autowired private val client: WebTestClient,
    @Autowired private val playerStep: PlayerStep,
    @Autowired private val playlistStep: PlaylistStep,
    @Autowired private val roomStep: RoomStep,
    @Autowired private val tokenService: TokenService,
    @Autowired private val objectMapper: ObjectMapper,
    @LocalServerPort private val port: Int,
) {

    private lateinit var player: PlayerResponse
    private lateinit var joiner: PlayerResponse
    private lateinit var playlist: PlaylistWithTrackResponse

    @BeforeEach
    fun setUp() {
        player = playerStep.save(dummyPlayerRequest())
        joiner = playerStep.save(dummyPlayerRequest(nickname = "joiner", registrationId = "joinerId"))
        playlist = playlistStep.save(player, dummyPlaylistCreationRequest())
    }

    @Test
    fun `방장이 시작하면 ROUND_STARTED가 정답 없이 전달된다`() {
        val room = roomStep.save(player, dummyRoomRequest(playlist.id))
        val (sessionA, sessionB, events) = subscribeAndJoin(room.id)

        roomStep.start(player.id, room.id)

        await().pollInterval(Duration.ofMillis(100)).atMost(Duration.ofSeconds(5)).untilAsserted {
            val started = events.filterIsInstance<RoundStartedEventMessage>()
            assertThat(started).hasSize(1)
            val event = started.first()
            assertThat(event.roundSeq).isEqualTo(1)
            assertThat(event.totalRounds).isEqualTo(1)
            assertThat(event.deadlineAt).isGreaterThan(0)
            assertThat(event.embedId).isEqualTo(TRACK_EMBED_ID)
        }

        sessionA.disconnect()
        sessionB.disconnect()
    }

    @Test
    fun `정답을 맞히면 ROUND_REVEALED로 정답·점수가 공개되고 원문은 방송되지 않는다`() {
        val room = roomStep.save(player, dummyRoomRequest(playlist.id))
        val (sessionA, sessionB, events) = subscribeAndJoin(room.id)
        roomStep.start(player.id, room.id)
        awaitRoundStarted(events)

        sessionB.send("/app/rooms/chat", RoomChatRequest(content = TRACK_TITLE))

        await().pollInterval(Duration.ofMillis(100)).atMost(Duration.ofSeconds(5)).untilAsserted {
            val revealed = events.filterIsInstance<RoundRevealedEventMessage>()
            assertThat(revealed).hasSize(1)
            val event = revealed.first()
            assertThat(event.winnerId).isEqualTo(joiner.id)
            assertThat(event.title).isEqualTo(TRACK_TITLE)
            assertThat(event.scores.first { it.playerId == joiner.id }.score).isEqualTo(1)
        }
        // 정답 채팅 원문은 누출 차단을 위해 일반 CHAT으로 방송되지 않는다.
        assertThat(events.filterIsInstance<RoomChatEventMessage>().map { it.content }).doesNotContain(TRACK_TITLE)

        sessionA.disconnect()
        sessionB.disconnect()
    }

    @Test
    fun `오답은 일반 CHAT으로 방송된다`() {
        val room = roomStep.save(player, dummyRoomRequest(playlist.id))
        val (sessionA, sessionB, events) = subscribeAndJoin(room.id)
        roomStep.start(player.id, room.id)
        awaitRoundStarted(events)

        sessionB.send("/app/rooms/chat", RoomChatRequest(content = "전혀 다른 추측"))

        await().pollInterval(Duration.ofMillis(100)).atMost(Duration.ofSeconds(5)).untilAsserted {
            val chats = events.filterIsInstance<RoomChatEventMessage>()
            assertThat(chats.map { it.content }).contains("전혀 다른 추측")
        }
        assertThat(events.filterIsInstance<RoundRevealedEventMessage>()).isEmpty()

        sessionA.disconnect()
        sessionB.disconnect()
    }

    @Test
    fun `마지막 라운드가 끝나면 ENDED로 방이 ACTIVE로 복귀한다`() {
        val room = roomStep.save(player, dummyRoomRequest(playlist.id))
        val (sessionA, sessionB, events) = subscribeAndJoin(room.id)
        roomStep.start(player.id, room.id)
        awaitRoundStarted(events)
        sessionB.send("/app/rooms/chat", RoomChatRequest(content = TRACK_TITLE))

        // 단일 트랙이므로 REVEAL(5초) 후 sweeper가 ENDED로 전이하고 방 상태를 ACTIVE로 되돌린다.
        await().pollInterval(Duration.ofMillis(200)).atMost(Duration.ofSeconds(15)).untilAsserted {
            val ended = events.filterIsInstance<GameEndedEventMessage>()
            assertThat(ended).hasSize(1)
            assertThat(ended.first().playerId).isNull()
            assertThat(getRoomDetail(room.id)?.status?.name).isEqualTo("ACTIVE")
        }

        sessionA.disconnect()
        sessionB.disconnect()
    }

    private fun subscribeAndJoin(roomId: Long): Triple<StompSession, StompSession, LinkedBlockingQueue<RoomEventMessage>> {
        val sessionA = connectStomp(objectMapper, tokenService, port, player, roomId, "password")
        val events = LinkedBlockingQueue<RoomEventMessage>()
        sessionA.subscribe("/topic/rooms/$roomId", object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = RoomEventMessage::class.java
            override fun handleFrame(headers: StompHeaders, payload: Any?) {
                events.add(payload as RoomEventMessage)
            }
        })

        // 구독 등록을 joiner 입장 시 브로드캐스트되는 JOINED 수신으로 확인한다(고정 sleep 없이 동기화).
        val sessionB = connectStomp(objectMapper, tokenService, port, joiner, roomId, "password")
        await().pollInterval(Duration.ofMillis(100)).atMost(Duration.ofSeconds(5)).untilAsserted {
            assertThat(events.filterIsInstance<RoomJoinedEventMessage>()).isNotEmpty
        }
        events.clear()
        return Triple(sessionA, sessionB, events)
    }

    private fun awaitRoundStarted(events: LinkedBlockingQueue<RoomEventMessage>) {
        await().pollInterval(Duration.ofMillis(100)).atMost(Duration.ofSeconds(5)).untilAsserted {
            assertThat(events.filterIsInstance<RoundStartedEventMessage>()).isNotEmpty
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
}
