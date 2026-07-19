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
import ilpak.nomat.playlist.application.dto.PlaylistCreationRequestTrack
import ilpak.nomat.playlist.application.dto.PlaylistWithTrackResponse
import ilpak.nomat.room.application.domain.RoundPhase
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

private const val FIRST_EMBED_ID = "prebufferEmbedA"
private const val SECOND_EMBED_ID = "prebufferEmbedB"

/**
 * REVEAL 구간 선버퍼링을 위한 다음 라운드 재생 참조 전달 검증.
 *
 * 클립을 1초로 짧게 잡아(`openDuration = 클립 1초 + 버퍼 2초 = 3초`) 정답 제출 없이 sweeper의
 * 마감 전이만으로 REVEAL에 도달시킨다. 트랙 순서는 게임 시작 시 셔플되므로 **어느 트랙이 1라운드인지
 * 단정하지 않고**, 다음 트랙이 현재 트랙과 다른 나머지 하나라는 관계로 검증한다.
 */
@IntegrationTest
class RoomRoundPrebufferIntegrationTest(
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

    @BeforeEach
    fun setUp() {
        player = playerStep.save(dummyPlayerRequest())
        joiner = playerStep.save(dummyPlayerRequest(nickname = "joiner", registrationId = "joinerId"))
    }

    @Test
    fun `중간 라운드의 ROUND_REVEALED는 다음 라운드 재생 참조를 동봉한다`() {
        val room = roomStep.save(player, dummyRoomRequest(twoTrackPlaylist().id))
        val (sessionA, sessionB, events) = subscribeAndJoin(room.id)

        roomStep.start(player.id, room.id)
        val started = awaitFirstRoundStarted(events)

        await().pollInterval(Duration.ofMillis(200)).atMost(Duration.ofSeconds(10)).untilAsserted {
            val revealed = events.filterIsInstance<RoundRevealedEventMessage>()
            assertThat(revealed).isNotEmpty
            val nextTrack = revealed.first().nextTrack
            assertThat(nextTrack).isNotNull
            // 셔플되므로 어느 쪽이 1라운드인지는 모른다 — 남은 하나가 다음 트랙이어야 한다.
            assertThat(nextTrack!!.embedId).isNotEqualTo(started.embedId)
            assertThat(nextTrack.embedId).isIn(FIRST_EMBED_ID, SECOND_EMBED_ID)
        }

        sessionA.disconnect()
        sessionB.disconnect()
    }

    @Test
    fun `마지막 라운드의 ROUND_REVEALED에는 다음 재생 참조가 없다`() {
        // 트랙이 하나뿐이면 첫 라운드가 곧 마지막 라운드다.
        val room = roomStep.save(player, dummyRoomRequest(singleTrackPlaylist().id))
        val (sessionA, sessionB, events) = subscribeAndJoin(room.id)

        roomStep.start(player.id, room.id)

        await().pollInterval(Duration.ofMillis(200)).atMost(Duration.ofSeconds(10)).untilAsserted {
            val revealed = events.filterIsInstance<RoundRevealedEventMessage>()
            assertThat(revealed).isNotEmpty
            assertThat(revealed.first().nextTrack).isNull()
        }

        sessionA.disconnect()
        sessionB.disconnect()
    }

    @Test
    fun `roundNumber는 전이 epoch가 아니라 라운드 번호를 센다`() {
        // 회귀 방지: `roundSeq`는 전이마다 +1 되는 CAS epoch라 라운드당 2씩 증가한다.
        // 이를 화면에 그대로 쓰면 "13/9"처럼 총 라운드 수를 넘는 표기가 나온다.
        val room = roomStep.save(player, dummyRoomRequest(twoTrackPlaylist().id))
        val (sessionA, sessionB, events) = subscribeAndJoin(room.id)

        roomStep.start(player.id, room.id)
        val first = awaitFirstRoundStarted(events)
        assertThat(first.roundNumber).isEqualTo(1)
        assertThat(first.totalRounds).isEqualTo(2)

        // 2라운드는 REVEAL을 한 번 거쳐 열리므로 roundSeq는 이미 1을 넘어섰다.
        await().pollInterval(Duration.ofMillis(200)).atMost(Duration.ofSeconds(15)).untilAsserted {
            val started = events.filterIsInstance<RoundStartedEventMessage>()
            assertThat(started).hasSize(2)
            val second = started[1]
            assertThat(second.roundNumber).isEqualTo(2)
            assertThat(second.roundNumber).isLessThanOrEqualTo(second.totalRounds)
            // epoch는 라운드 번호보다 앞서 나간다 — 둘을 혼동하면 안 된다는 것이 이 테스트의 요지다.
            assertThat(second.roundSeq).isGreaterThan(second.roundNumber.toLong())
        }

        sessionA.disconnect()
        sessionB.disconnect()
    }

    @Test
    fun `재접속 스냅샷은 REVEAL에서만 다음 재생 참조를 포함한다`() {
        val room = roomStep.save(player, dummyRoomRequest(twoTrackPlaylist().id))
        val (sessionA, sessionB, events) = subscribeAndJoin(room.id)

        roomStep.start(player.id, room.id)
        awaitFirstRoundStarted(events)

        // OPEN 중에는 다음 트랙이 없어야 한다 — 있으면 다음 라운드 정답이 라운드 내내 노출된다.
        await().pollInterval(Duration.ofMillis(100)).atMost(Duration.ofSeconds(5)).untilAsserted {
            val round = getRoundSnapshot(room.id)
            assertThat(round?.phase).isEqualTo(RoundPhase.OPEN)
            assertThat(round?.nextTrack).isNull()
        }

        // REVEAL로 전이되면 이벤트를 놓친 재접속자도 같은 선버퍼링 기회를 얻어야 한다.
        await().pollInterval(Duration.ofMillis(200)).atMost(Duration.ofSeconds(10)).untilAsserted {
            val round = getRoundSnapshot(room.id)
            assertThat(round?.phase).isEqualTo(RoundPhase.REVEAL)
            assertThat(round?.nextTrack).isNotNull
            assertThat(round?.nextTrack?.embedId).isNotEqualTo(round?.currentTrack?.embedId)
        }

        sessionA.disconnect()
        sessionB.disconnect()
    }

    private fun singleTrackPlaylist(): PlaylistWithTrackResponse =
        playlistStep.save(
            player,
            dummyPlaylistCreationRequest(tracks = listOf(shortTrack(FIRST_EMBED_ID, "Prebuffer Track A", true))),
        )

    private fun twoTrackPlaylist(): PlaylistWithTrackResponse =
        playlistStep.save(
            player,
            dummyPlaylistCreationRequest(
                tracks = listOf(
                    shortTrack(FIRST_EMBED_ID, "Prebuffer Track A", true),
                    shortTrack(SECOND_EMBED_ID, "Prebuffer Track B", false),
                ),
            ),
        )

    // 클립을 1초로 잡아 OPEN 마감(클립 1초 + 버퍼 2초)이 테스트 대기 시간 안에 들어오게 한다.
    private fun shortTrack(embedId: String, title: String, representative: Boolean) =
        PlaylistCreationRequestTrack(
            embedId = embedId,
            title = title,
            startTimeSec = 0,
            endTimeSec = 1,
            repeatCount = 1,
            additionalTitles = emptySet(),
            isRepresentative = representative,
        )

    private fun subscribeAndJoin(
        roomId: Long,
    ): Triple<StompSession, StompSession, LinkedBlockingQueue<RoomEventMessage>> {
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

    private fun awaitFirstRoundStarted(events: LinkedBlockingQueue<RoomEventMessage>): RoundStartedEventMessage {
        await().pollInterval(Duration.ofMillis(100)).atMost(Duration.ofSeconds(5)).untilAsserted {
            assertThat(events.filterIsInstance<RoundStartedEventMessage>()).isNotEmpty
        }
        return events.filterIsInstance<RoundStartedEventMessage>().first()
    }

    private fun getRoundSnapshot(roomId: Long) =
        client.get().uri("/rooms/{roomId}", roomId)
            .auth(player)
            .exchange()
            .expectStatus().isOk()
            .expectBody<RoomDetailResponse>()
            .returnResult()
            .responseBody
            ?.round
}
