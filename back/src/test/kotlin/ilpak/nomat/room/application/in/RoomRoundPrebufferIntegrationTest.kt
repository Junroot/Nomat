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

private const val FIRST_EMBED_ID = "prebufferEmbedA"
private const val SECOND_EMBED_ID = "prebufferEmbedB"
private const val FIRST_TITLE = "Prebuffer Track A"
private const val SECOND_TITLE = "Prebuffer Track B"

// 정답 제출 전에 마감이 지나가지 않도록 넉넉히 잡은 클립 길이(초).
private const val LONG_CLIP_SEC = 30

/**
 * REVEAL 구간 선버퍼링을 위한 다음 라운드 재생 참조 전달 검증.
 *
 * REVEAL 진입 경로는 두 갈래이고 각자 `nextTrack`을 따로 조립하므로(`RoundService`의
 * `advanceDueRoom`과 `submitAnswer`) 양쪽을 모두 덮는다:
 *
 * - **마감 전이(sweeper)** — 클립을 1초로 짧게 잡아(`openDuration = 클립 1초 + 버퍼 2초 = 3초`)
 *   정답 제출 없이 마감이 지나가게 둔다.
 * - **정답 제출** — 클립을 30초로 길게 잡아 마감이 오기 전에 정답을 보낸다. sweeper가 먼저 전이해
 *   테스트가 헛돌지 않도록, 마감 경로에는 없는 `winnerId`로 어느 경로를 탔는지 못박는다.
 *
 * 트랙 순서는 게임 시작 시 셔플되므로 **어느 트랙이 1라운드인지 단정하지 않고**, 다음 트랙이
 * 현재 트랙과 다른 나머지 하나라는 관계로 검증한다.
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
    fun `정답 제출로 REVEAL에 진입해도 다음 라운드 재생 참조를 동봉한다`() {
        // 마감 전이(`advanceDueRoom`)와 정답 전이(`submitAnswer`)는 각자 `nextTrack`을 조립하는
        // 별개의 코드 경로다. 실제 게임에서 라운드는 타임아웃보다 정답으로 끝나는 쪽이 흔하므로,
        // 이 경로가 빠지면 대다수 라운드에서 선버퍼링이 조용히 무효화된다.
        val room = roomStep.save(player, dummyRoomRequest(twoLongTrackPlaylist().id))
        val (sessionA, sessionB, events) = subscribeAndJoin(room.id)

        roomStep.start(player.id, room.id)
        val started = awaitFirstRoundStarted(events)

        // 셔플되므로 어느 트랙이 1라운드인지는 이벤트의 embedId로만 알 수 있다.
        sessionB.send("/app/rooms/chat", RoomChatRequest(content = titleOf(started.embedId)))

        await().pollInterval(Duration.ofMillis(100)).atMost(Duration.ofSeconds(10)).untilAsserted {
            val revealed = events.filterIsInstance<RoundRevealedEventMessage>()
            assertThat(revealed).isNotEmpty
            val event = revealed.first()
            // 이 단언이 경로를 못박는다 — 마감 전이는 승자가 없어 `winnerId`가 null이다.
            // 클립을 30초로 잡아 마감이 오기 전이지만, sweeper가 끼어들면 여기서 잡힌다.
            assertThat(event.winnerId).isEqualTo(joiner.id)
            val nextTrack = event.nextTrack
            assertThat(nextTrack).isNotNull
            assertThat(nextTrack!!.embedId).isNotEqualTo(started.embedId)
            assertThat(nextTrack.embedId).isIn(FIRST_EMBED_ID, SECOND_EMBED_ID)
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
            dummyPlaylistCreationRequest(tracks = listOf(shortTrack(FIRST_EMBED_ID, FIRST_TITLE, true))),
        )

    private fun twoTrackPlaylist(): PlaylistWithTrackResponse =
        playlistStep.save(
            player,
            dummyPlaylistCreationRequest(
                tracks = listOf(
                    shortTrack(FIRST_EMBED_ID, FIRST_TITLE, true),
                    shortTrack(SECOND_EMBED_ID, SECOND_TITLE, false),
                ),
            ),
        )

    // 정답 제출 경로 전용. 클립을 30초로 잡아 OPEN 마감(30초 + 버퍼 2초)이 테스트 대기 시간 밖에
    // 놓이게 한다 — 그래야 sweeper가 먼저 REVEAL로 전이해 검증 대상 경로를 가로채지 못한다.
    private fun twoLongTrackPlaylist(): PlaylistWithTrackResponse =
        playlistStep.save(
            player,
            dummyPlaylistCreationRequest(
                tracks = listOf(
                    longTrack(FIRST_EMBED_ID, FIRST_TITLE, true),
                    longTrack(SECOND_EMBED_ID, SECOND_TITLE, false),
                ),
            ),
        )

    private fun titleOf(embedId: String): String =
        if (embedId == FIRST_EMBED_ID) FIRST_TITLE else SECOND_TITLE

    private fun longTrack(embedId: String, title: String, representative: Boolean) =
        shortTrack(embedId, title, representative).copy(endTimeSec = LONG_CLIP_SEC)

    // 클립을 1초로 잡아 OPEN 마감(클립 1초 + 버퍼 2초)이 테스트 대기 시간 안에 들어오게 한다.
    private fun shortTrack(embedId: String, title: String, representative: Boolean) =
        PlaylistCreationRequestTrack(
            embedId = embedId,
            title = title,
            startTimeSec = 0,
            endTimeSec = 1,
            repeatCount = 1,
            additionalTitles = emptyList(),
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
