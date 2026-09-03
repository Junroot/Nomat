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
import ilpak.nomat.infrastructure.integration.util.connectStomp
import ilpak.nomat.player.application.dto.PlayerResponse
import ilpak.nomat.playlist.application.dto.PlaylistWithTrackResponse
import ilpak.nomat.room.application.dto.RoomChatEventMessage
import ilpak.nomat.room.application.dto.RoomChatRequest
import ilpak.nomat.room.application.dto.RoomEventMessage
import ilpak.nomat.room.application.dto.RoomJoinedEventMessage
import ilpak.nomat.room.application.dto.RoomPassRequest
import ilpak.nomat.room.application.dto.RoundPassUpdatedEventMessage
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
import java.lang.reflect.Type
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue

/** 포기 현황이 실시간 경로(`room:{id}:events` pub/sub → STOMP)로 나가는 방식의 계약. */
@IntegrationTest
class RoomRoundPassStompIntegrationTest(
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
    fun `포기 현황은 인원수만 담고 누가 눌렀는지는 담지 않는다`() {
        val room = roomStep.save(player, dummyRoomRequest(playlist.id))
        val (sessionA, sessionB, events) = subscribeAndJoin(room.id)
        roomStep.start(player.id, room.id)
        val roundSeq = awaitRoundStarted(events)

        sessionB.send("/app/rooms/pass", RoomPassRequest(roundSeq = roundSeq))

        await().pollInterval(Duration.ofMillis(100)).atMost(Duration.ofSeconds(5)).untilAsserted {
            val updates = events.filterIsInstance<RoundPassUpdatedEventMessage>()
            assertThat(updates).hasSize(1)
            val update = updates.first()
            assertThat(update.roundSeq).isEqualTo(roundSeq)
            assertThat(update.passedCount).isEqualTo(1)
            // 2명이 남아 있으므로 임계는 2 — 아직 전이되지 않는다.
            assertThat(update.requiredCount).isEqualTo(2)
            assertThat(update.playerId).isNull()
            assertThat(update.nickname).isNull()
        }
        assertThat(events.filterIsInstance<RoundRevealedEventMessage>()).isEmpty()

        sessionA.disconnect()
        sessionB.disconnect()
    }

    @Test
    fun `임계에 도달하면 ROUND_REVEALED만 나가고 포기 현황은 나가지 않는다`() {
        val room = roomStep.save(player, dummyRoomRequest(playlist.id))
        val (sessionA, sessionB, events) = subscribeAndJoin(room.id)
        roomStep.start(player.id, room.id)
        val roundSeq = awaitRoundStarted(events)
        sessionB.send("/app/rooms/pass", RoomPassRequest(roundSeq = roundSeq))
        await().pollInterval(Duration.ofMillis(100)).atMost(Duration.ofSeconds(5)).untilAsserted {
            assertThat(events.filterIsInstance<RoundPassUpdatedEventMessage>()).hasSize(1)
        }

        sessionA.send("/app/rooms/pass", RoomPassRequest(roundSeq = roundSeq))

        await().pollInterval(Duration.ofMillis(100)).atMost(Duration.ofSeconds(5)).untilAsserted {
            val revealed = events.filterIsInstance<RoundRevealedEventMessage>()
            assertThat(revealed).hasSize(1)
            assertThat(revealed.first().winnerId).isNull()
        }
        // 전이를 일으킨 호출은 포기 현황을 발행하지 않는다 — 클라이언트가 REVEAL로 넘어간 뒤
        // 포기 카운트를 다시 그리게 되기 때문이다.
        assertThat(events.filterIsInstance<RoundPassUpdatedEventMessage>()).hasSize(1)

        sessionA.disconnect()
        sessionB.disconnect()
    }

    @Test
    fun `포기한 참가자의 채팅도 그대로 방송된다`() {
        val room = roomStep.save(player, dummyRoomRequest(playlist.id))
        val (sessionA, sessionB, events) = subscribeAndJoin(room.id)
        roomStep.start(player.id, room.id)
        val roundSeq = awaitRoundStarted(events)
        sessionB.send("/app/rooms/pass", RoomPassRequest(roundSeq = roundSeq))

        sessionB.send("/app/rooms/chat", RoomChatRequest(content = "이건 모르겠다"))

        // 포기의 대가는 게임에서 빠지는 것이 아니라 그 라운드의 채점에서만 빠지는 것이다.
        await().pollInterval(Duration.ofMillis(100)).atMost(Duration.ofSeconds(5)).untilAsserted {
            val chats = events.filterIsInstance<RoomChatEventMessage>()
            assertThat(chats.map { it.content }).contains("이건 모르겠다")
            assertThat(chats.first { it.content == "이건 모르겠다" }.nickname).isEqualTo("joiner")
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

    private fun awaitRoundStarted(events: LinkedBlockingQueue<RoomEventMessage>): Long {
        await().pollInterval(Duration.ofMillis(100)).atMost(Duration.ofSeconds(5)).untilAsserted {
            assertThat(events.filterIsInstance<RoundStartedEventMessage>()).isNotEmpty
        }
        return events.filterIsInstance<RoundStartedEventMessage>().first().roundSeq
    }
}
