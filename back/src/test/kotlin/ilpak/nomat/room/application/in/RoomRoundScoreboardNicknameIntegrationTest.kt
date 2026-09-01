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
import ilpak.nomat.playlist.application.dto.PlaylistCreationRequestTrack
import ilpak.nomat.playlist.application.dto.PlaylistWithTrackResponse
import ilpak.nomat.room.application.RoomService
import ilpak.nomat.room.application.RoundScoreboardAssembler
import ilpak.nomat.room.application.RoundService
import ilpak.nomat.room.application.domain.RoundPhase
import ilpak.nomat.room.application.domain.ScoreEntry
import ilpak.nomat.room.application.dto.RoomChatRequest
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
import java.lang.reflect.Type
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue

private const val TRACK_TITLE = "Test Track"
private const val HOST_NICKNAME = "testUser"
private const val JOINER_NICKNAME = "joiner"

/**
 * 점수판·승자 닉네임을 서버가 해석해 싣는지 검증한다(이슈 #235).
 *
 * 결함의 본질은 표기가 아니라 **신원 해석 주체**였다 — 프론트가 휘발성 멤버 목록과 조인해 이름을
 * 붙이는 한, 점수판 스냅샷보다 멤버 목록이 먼저 줄어드는 순간 이름이 사라진다. 따라서 검증도
 * "화면에 뭐가 보이나"가 아니라 **닉네임이 방 멤버십과 무관하게 해석되는가**를 겨눈다.
 */
@IntegrationTest
class RoomRoundScoreboardNicknameIntegrationTest(
    @Autowired private val roomService: RoomService,
    @Autowired private val roundService: RoundService,
    @Autowired private val scoreboardAssembler: RoundScoreboardAssembler,
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
        player = playerStep.save(dummyPlayerRequest(nickname = HOST_NICKNAME))
        joiner = playerStep.save(
            dummyPlayerRequest(nickname = JOINER_NICKNAME, registrationId = "joinerId"),
        )
    }

    @Test
    fun `ROUND_REVEALED가 점수판 닉네임과 승자 닉네임을 함께 싣는다`() {
        val room = roomStep.save(player, dummyRoomRequest(longTrackPlaylist().id))
        val (sessionA, sessionB, events) = subscribeAndJoin(room.id)
        roomStep.start(player.id, room.id)
        awaitRoundStarted(events)

        sessionB.send("/app/rooms/chat", RoomChatRequest(content = TRACK_TITLE))

        await().pollInterval(Duration.ofMillis(100)).atMost(Duration.ofSeconds(10)).untilAsserted {
            val revealed = events.filterIsInstance<RoundRevealedEventMessage>()
            assertThat(revealed).isNotEmpty
            val event = revealed.first()
            assertThat(event.winnerId).isEqualTo(joiner.id)
            assertThat(event.winnerNickname).isEqualTo(JOINER_NICKNAME)
            assertThat(event.scores).isNotEmpty
            assertThat(event.scores.map { it.nickname })
                .containsExactlyInAnyOrder(HOST_NICKNAME, JOINER_NICKNAME)
        }

        sessionA.disconnect()
        sessionB.disconnect()
    }

    @Test
    fun `타임아웃 공개에는 승자도 승자 닉네임도 없다`() {
        // 클립 1초 + 버퍼 2초로 마감이 테스트 대기 시간 안에 들어온다 — 아무도 맞히지 않고 sweeper가 공개한다.
        val room = roomStep.save(player, dummyRoomRequest(shortTrackPlaylist().id))
        val (sessionA, sessionB, events) = subscribeAndJoin(room.id)

        roomStep.start(player.id, room.id)

        await().pollInterval(Duration.ofMillis(200)).atMost(Duration.ofSeconds(10)).untilAsserted {
            val revealed = events.filterIsInstance<RoundRevealedEventMessage>()
            assertThat(revealed).isNotEmpty
            val event = revealed.first()
            assertThat(event.winnerId).isNull()
            assertThat(event.winnerNickname).isNull()
            // 승자가 없어도 점수판 항목은 이름을 잃지 않는다.
            assertThat(event.scores.map { it.nickname })
                .containsExactlyInAnyOrder(HOST_NICKNAME, JOINER_NICKNAME)
        }

        sessionA.disconnect()
        sessionB.disconnect()
    }

    @Test
    fun `재접속 스냅샷의 점수판도 닉네임을 담고 REVEAL이면 승자 닉네임도 담는다`() {
        // 전이를 직접 구동해 REVEAL 구간(5초)을 놓칠 여지를 없앤다 — 스냅샷 형태만이 관심사다.
        val room = roomStep.save(player, dummyRoomRequest(longTrackPlaylist().id))
        roomStep.join(player.id, room.id, "password")
        roomStep.join(joiner.id, room.id, "password")
        roomStep.start(player.id, room.id)

        val open = roomService.getDetail(room.id, player.id).round!!
        assertThat(open.phase).isEqualTo(RoundPhase.OPEN)
        assertThat(open.winnerNickname).isNull()
        assertThat(open.scores.map { it.nickname })
            .containsExactlyInAnyOrder(HOST_NICKNAME, JOINER_NICKNAME)

        roundService.submitAnswer(room.id, joiner.id, TRACK_TITLE)

        val revealed = roomService.getDetail(room.id, player.id).round!!
        assertThat(revealed.phase).isEqualTo(RoundPhase.REVEAL)
        assertThat(revealed.winnerId).isEqualTo(joiner.id)
        assertThat(revealed.winnerNickname).isEqualTo(JOINER_NICKNAME)
        assertThat(revealed.scores.map { it.nickname })
            .containsExactlyInAnyOrder(HOST_NICKNAME, JOINER_NICKNAME)
    }

    @Test
    fun `퇴장한 플레이어의 점수판 항목과 승자 닉네임도 해석된다`() {
        // 이슈 #235의 회귀 방어. 서버의 살아있는 점수판은 퇴장자를 지우므로(`onPlayerLeft` → ZREM),
        // 실제로 문제가 됐던 것은 클라이언트가 붙들고 있는 **얼어붙은 점수판 스냅샷**이다.
        // 그 스냅샷을 그대로 조립기에 넣어, 해석이 방 멤버십이 아니라 `player` 저장소를 보는지 못박는다.
        val room = roomStep.save(player, dummyRoomRequest(longTrackPlaylist().id))
        roomStep.join(player.id, room.id, "password")
        roomStep.join(joiner.id, room.id, "password")
        roomStep.start(player.id, room.id)
        roomService.leave(room.id, joiner.id)

        val frozen = listOf(ScoreEntry(player.id, 4), ScoreEntry(joiner.id, 14))
        val scoreboard = scoreboardAssembler.assemble(frozen, winnerId = joiner.id)

        assertThat(scoreboard.entries.map { it.nickname })
            .containsExactly(HOST_NICKNAME, JOINER_NICKNAME)
        assertThat(scoreboard.winnerNickname).isEqualTo(JOINER_NICKNAME)
    }

    @Test
    fun `해석되지 않는 id는 예외 없이 중립 라벨로 채워진다`() {
        // 여기서 예외가 나면 이름 하나 때문에 그 라운드의 공개 방송 자체가 죽는다.
        val unknownId = 9_999_999L

        val scoreboard = scoreboardAssembler.assemble(
            listOf(ScoreEntry(player.id, 1), ScoreEntry(unknownId, 2)),
            winnerId = unknownId,
        )

        assertThat(scoreboard.entries.map { it.nickname })
            .containsExactly(HOST_NICKNAME, RoundScoreboardAssembler.UNKNOWN_NICKNAME)
        assertThat(scoreboard.winnerNickname).isEqualTo(RoundScoreboardAssembler.UNKNOWN_NICKNAME)
        // 퇴장 여부는 이 지점에서 알 수 없는 정보다 — 그렇게 단정한 것이 애초의 결함이었다.
        assertThat(RoundScoreboardAssembler.UNKNOWN_NICKNAME).doesNotContain("퇴장")
    }

    // 정답 제출 전에 마감이 지나가지 않도록 넉넉히 잡은 기본 트랙(클립 180초).
    private fun longTrackPlaylist(): PlaylistWithTrackResponse =
        playlistStep.save(player, dummyPlaylistCreationRequest())

    private fun shortTrackPlaylist(): PlaylistWithTrackResponse =
        playlistStep.save(
            player,
            dummyPlaylistCreationRequest(
                tracks = listOf(
                    PlaylistCreationRequestTrack(
                        embedId = "testEmbedId",
                        title = TRACK_TITLE,
                        startTimeSec = 0,
                        endTimeSec = 1,
                        repeatCount = 1,
                        additionalTitles = emptySet(),
                        isRepresentative = true,
                    ),
                ),
            ),
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

    private fun awaitRoundStarted(events: LinkedBlockingQueue<RoomEventMessage>) {
        await().pollInterval(Duration.ofMillis(100)).atMost(Duration.ofSeconds(5)).untilAsserted {
            assertThat(events.filterIsInstance<RoundStartedEventMessage>()).isNotEmpty
        }
    }
}
