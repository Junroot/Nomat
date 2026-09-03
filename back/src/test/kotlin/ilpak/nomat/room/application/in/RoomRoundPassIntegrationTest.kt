package ilpak.nomat.room.application.`in`

import ilpak.nomat.infrastructure.integration.IntegrationTest
import ilpak.nomat.infrastructure.integration.step.PlayerStep
import ilpak.nomat.infrastructure.integration.step.PlaylistStep
import ilpak.nomat.infrastructure.integration.step.RoomStep
import ilpak.nomat.infrastructure.integration.step.dummyPlayerRequest
import ilpak.nomat.infrastructure.integration.step.dummyPlaylistCreationRequest
import ilpak.nomat.infrastructure.integration.step.dummyRoomRequest
import ilpak.nomat.playlist.application.dto.PlaylistCreationRequestTrack
import ilpak.nomat.player.application.dto.PlayerResponse
import ilpak.nomat.room.application.RoomService
import ilpak.nomat.room.application.RoundService
import ilpak.nomat.room.application.domain.RoomPlaylistTrackRepository
import ilpak.nomat.room.application.domain.RoundPhase
import ilpak.nomat.room.application.domain.RoundStateStore
import ilpak.nomat.room.application.dto.RoundSnapshotResponse
import ilpak.nomat.room.out.RoundRedisKeys
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration

/**
 * 포기 신호가 `RoundService`를 통과할 때의 계약 — 정답 판정 제외, 라운드/게임 경계의 잔재 폐기,
 * 스냅샷 복원, 포기로 끝난 라운드의 이후 진행.
 */
@IntegrationTest
class RoomRoundPassIntegrationTest(
    @Autowired private val roomService: RoomService,
    @Autowired private val roundService: RoundService,
    @Autowired private val roundStateStore: RoundStateStore,
    @Autowired private val trackRepository: RoomPlaylistTrackRepository,
    @Autowired private val redisTemplate: StringRedisTemplate,
    @Autowired private val playerStep: PlayerStep,
    @Autowired private val playlistStep: PlaylistStep,
    @Autowired private val roomStep: RoomStep,
) {

    private lateinit var player: PlayerResponse
    private lateinit var joiner: PlayerResponse

    @BeforeEach
    fun setUp() {
        player = playerStep.save(dummyPlayerRequest())
        joiner = playerStep.save(dummyPlayerRequest(nickname = "joiner", registrationId = "joinerId"))
    }

    @Test
    fun `포기 중인 참가자의 정답은 승자로 기록되지 않는다`() {
        val roomId = startedRoom(members = listOf(player, joiner))
        // 2명이라 임계는 2 — 한 명의 포기로는 라운드가 전이되지 않아 정답 판정만 따로 볼 수 있다.
        roundService.pass(roomId, joiner.id, currentRoundSeq(roomId))

        roundService.submitAnswer(roomId, joiner.id, currentTitle(roomId))

        val snapshot = roundStateStore.snapshot(roomId)!!
        assertThat(snapshot.phase).isEqualTo(RoundPhase.OPEN)
        assertThat(snapshot.winnerId).isNull()
        assertThat(snapshot.scores.first { it.playerId == joiner.id }.score).isEqualTo(0)
    }

    @Test
    fun `포기를 취소하면 정답 판정이 즉시 복원된다`() {
        val roomId = startedRoom(members = listOf(player, joiner))
        val roundSeq = currentRoundSeq(roomId)
        roundService.pass(roomId, joiner.id, roundSeq)

        roundService.pass(roomId, joiner.id, roundSeq)
        roundService.submitAnswer(roomId, joiner.id, currentTitle(roomId))

        val snapshot = roundStateStore.snapshot(roomId)!!
        assertThat(snapshot.phase).isEqualTo(RoundPhase.REVEAL)
        assertThat(snapshot.winnerId).isEqualTo(joiner.id)
        assertThat(snapshot.scores.first { it.playerId == joiner.id }.score).isEqualTo(1)
    }

    @Test
    fun `이전 라운드에서 포기했던 참가자도 다음 라운드에서는 정답이 인정된다`() {
        // 아무 토글 없이 라운드만 넘어가면 `passes`는 남고 `passSeq`만 stale이 된다. 이 판정이 빠지면
        // 그 참가자가 게임 내내 이길 수 없는 침묵 실패가 난다.
        val roomId = startedRoom(members = listOf(player, joiner), trackCount = 2)
        roundService.pass(roomId, joiner.id, currentRoundSeq(roomId))
        advanceToNextOpen(roomId)
        assertThat(redisTemplate.opsForSet().members(RoundRedisKeys.passes(roomId)))
            .containsExactly(joiner.id.toString())

        roundService.submitAnswer(roomId, joiner.id, currentTitle(roomId))

        val snapshot = roundStateStore.snapshot(roomId)!!
        assertThat(snapshot.phase).isEqualTo(RoundPhase.REVEAL)
        assertThat(snapshot.winnerId).isEqualTo(joiner.id)
        assertThat(snapshot.scores.first { it.playerId == joiner.id }.score).isEqualTo(1)
    }

    @Test
    fun `게임이 자연 종료된 뒤 다시 시작하면 포기 상태가 남아 있지 않다`() {
        // 자연 종료(ENDED) 경로에는 teardown이 없어 포기 집합이 TTL로 살아남는데, 재시작하면
        // roundSeq가 1로 되돌아가 lazy reset 판별식이 잔재를 "유효"로 읽는다.
        val roomId = startedRoom(members = listOf(player))
        roundService.pass(roomId, player.id, currentRoundSeq(roomId))
        awaitGameEnded(roomId)

        roomStep.start(player.id, roomId)

        val round = roundOf(roomId, player.id)
        assertThat(round.roundNumber).isEqualTo(1)
        assertThat(round.passedCount).isEqualTo(0)
        assertThat(round.passed).isFalse()
        roundService.submitAnswer(roomId, player.id, currentTitle(roomId))
        val snapshot = roundStateStore.snapshot(roomId)!!
        assertThat(snapshot.winnerId).isEqualTo(player.id)
        assertThat(snapshot.scores.first { it.playerId == player.id }.score).isEqualTo(1)
    }

    @Test
    fun `OPEN 중 재접속 스냅샷은 본인의 포기 여부와 인원수만 담는다`() {
        val roomId = startedRoom(members = listOf(player, joiner))
        roundService.pass(roomId, joiner.id, currentRoundSeq(roomId))

        val mine = roundOf(roomId, joiner.id)
        val others = roundOf(roomId, player.id)

        assertThat(mine.passed).isTrue()
        assertThat(mine.passedCount).isEqualTo(1)
        assertThat(mine.requiredCount).isEqualTo(2)
        assertThat(others.passed).isFalse()
        assertThat(others.passedCount).isEqualTo(1)
        // 누가 눌렀는지는 어떤 형태로도 내려가지 않는다.
        assertThat(RoundSnapshotResponse::class.java.declaredFields.map { it.name })
            .doesNotContain("passedPlayerIds", "passedPlayers")
    }

    @Test
    fun `포기로 끝난 라운드도 다음 라운드로 진행하고 마지막이면 게임이 끝난다`() {
        val roomId = startedRoom(members = listOf(player), trackCount = 2)

        roundService.pass(roomId, player.id, currentRoundSeq(roomId))
        assertThat(roundStateStore.snapshot(roomId)!!.winnerId).isNull()
        advanceToNextOpen(roomId)
        assertThat(roundOf(roomId, player.id).roundNumber).isEqualTo(2)

        roundService.pass(roomId, player.id, currentRoundSeq(roomId))
        awaitGameEnded(roomId)
    }

    /** 멤버를 모두 입장시키고 게임을 시작한 방의 id. 혼자면 임계가 1이라 포기 한 번으로 전이된다. */
    private fun startedRoom(members: List<PlayerResponse>, trackCount: Int = 1): Long {
        val playlist = playlistStep.save(player, dummyPlaylistCreationRequest(tracks = tracks(trackCount)))
        val roomId = roomStep.save(player, dummyRoomRequest(playlist.id)).id
        members.forEach { roomStep.join(it.id, roomId, "password") }
        roomStep.start(player.id, roomId)
        return roomId
    }

    private fun tracks(count: Int): List<PlaylistCreationRequestTrack> = (1..count).map {
        PlaylistCreationRequestTrack(
            embedId = "embed$it",
            title = "Track $it",
            startTimeSec = 0,
            endTimeSec = 180,
            repeatCount = 1,
            additionalTitles = emptyList(),
            isRepresentative = it == 1,
        )
    }

    private fun currentRoundSeq(roomId: Long): Long = roundStateStore.snapshot(roomId)!!.roundSeq

    /** 현재 `OPEN` 라운드의 정답. 트랙 순서는 시작 시 셔플되므로 상태에서 되짚는다. */
    private fun currentTitle(roomId: Long): String {
        val currentTrackId = roundStateStore.snapshot(roomId)!!.currentTrackId
        return trackRepository.findByRoomId(roomId).first { it.trackId == currentTrackId }.title
    }

    private fun roundOf(roomId: Long, playerId: Long): RoundSnapshotResponse =
        roomService.getDetail(roomId, playerId).round!!

    /** `REVEAL` 마감을 과거로 당겨 다음 라운드가 열릴 때까지 sweeper를 구동한다. */
    private fun advanceToNextOpen(roomId: Long) {
        expireDeadline(roomId)
        await().pollInterval(Duration.ofMillis(100)).atMost(Duration.ofSeconds(10)).untilAsserted {
            expireDeadline(roomId)
            roundService.sweepDueRounds()
            assertThat(roundStateStore.snapshot(roomId)!!.phase).isEqualTo(RoundPhase.OPEN)
        }
    }

    private fun awaitGameEnded(roomId: Long) {
        await().pollInterval(Duration.ofMillis(100)).atMost(Duration.ofSeconds(10)).untilAsserted {
            expireDeadline(roomId)
            roundService.sweepDueRounds()
            assertThat(roundStateStore.snapshot(roomId)!!.phase).isEqualTo(RoundPhase.ENDED)
        }
    }

    private fun expireDeadline(roomId: Long) {
        redisTemplate.opsForHash<String, String>().put(RoundRedisKeys.round(roomId), "deadlineAt", "1")
        redisTemplate.opsForZSet().add(RoundRedisKeys.deadlines(roomId), roomId.toString(), 1.0)
    }
}
