package ilpak.nomat.room.application.`in`

import ilpak.nomat.infrastructure.integration.IntegrationTest
import ilpak.nomat.infrastructure.integration.step.PlayerStep
import ilpak.nomat.infrastructure.integration.step.PlaylistStep
import ilpak.nomat.infrastructure.integration.step.RoomStep
import ilpak.nomat.infrastructure.integration.step.dummyPlayerRequest
import ilpak.nomat.infrastructure.integration.step.dummyPlaylistCreationRequest
import ilpak.nomat.infrastructure.integration.step.dummyRoomRequest
import ilpak.nomat.player.application.dto.PlayerResponse
import ilpak.nomat.playlist.application.dto.PlaylistWithTrackResponse
import ilpak.nomat.room.application.RoomService
import ilpak.nomat.room.application.RoundService
import ilpak.nomat.room.application.domain.RoundPhase
import ilpak.nomat.room.application.domain.RoundStateStore
import ilpak.nomat.room.application.domain.RoundTrackSpec
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration

@IntegrationTest
class RoomRoundLifecycleIntegrationTest(
    @Autowired private val roomService: RoomService,
    @Autowired private val roundService: RoundService,
    @Autowired private val roundStateStore: RoundStateStore,
    @Autowired private val playerStep: PlayerStep,
    @Autowired private val playlistStep: PlaylistStep,
    @Autowired private val roomStep: RoomStep,
) {

    private lateinit var player: PlayerResponse
    private lateinit var joiner: PlayerResponse
    private lateinit var playlist: PlaylistWithTrackResponse
    private var roomId: Long = 0

    @BeforeEach
    fun setUp() {
        player = playerStep.save(dummyPlayerRequest())
        joiner = playerStep.save(dummyPlayerRequest(nickname = "joiner", registrationId = "joinerId"))
        playlist = playlistStep.save(player, dummyPlaylistCreationRequest())
        roomId = roomStep.save(player, dummyRoomRequest(playlist.id)).id
    }

    @Test
    fun `게임 중 퇴장하면 점수판에서 제거된다`() {
        roomStep.join(player.id, roomId, "password")
        roomStep.join(joiner.id, roomId, "password")
        roomStep.start(player.id, roomId)
        assertThat(roundStateStore.scoreboard(roomId).map { it.playerId }).contains(joiner.id)

        roomService.leave(roomId, joiner.id)

        assertThat(roundStateStore.scoreboard(roomId).map { it.playerId })
            .doesNotContain(joiner.id)
            .contains(player.id)
    }

    @Test
    fun `OPEN 중 재접속 스냅샷에는 정답이 노출되지 않는다`() {
        roomStep.join(player.id, roomId, "password")
        roomStep.start(player.id, roomId)

        val detail = roomService.getDetail(roomId, player.id)

        val round = detail.round!!
        assertThat(round.phase).isEqualTo(RoundPhase.OPEN)
        assertThat(round.title).isNull()
        assertThat(round.currentTrack.embedId).isEqualTo("testEmbedId")
        assertThat(round.scores.map { it.playerId }).contains(player.id)
    }

    @Test
    fun `유령 방은 sweeper가 정리한다`() {
        val ghostRoomId = 9_999_999L
        roundStateStore.start(ghostRoomId, listOf(RoundTrackSpec(1L, 0)), setOf(1L))

        roundService.sweepDueRounds()

        await().pollInterval(Duration.ofMillis(200)).atMost(Duration.ofSeconds(5)).untilAsserted {
            assertThat(roundStateStore.snapshot(ghostRoomId)).isNull()
        }
    }
}
