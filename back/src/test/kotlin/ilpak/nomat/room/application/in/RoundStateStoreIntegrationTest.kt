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
import ilpak.nomat.room.application.RoundService
import ilpak.nomat.room.application.domain.RoundPhase
import ilpak.nomat.room.application.domain.RoundStateStore
import ilpak.nomat.room.application.domain.RoundTrackSpec
import ilpak.nomat.room.application.domain.RoundTransition
import ilpak.nomat.room.application.domain.TransitionResult
import ilpak.nomat.room.out.RoundRedisKeys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.RedisCallback
import org.springframework.data.redis.core.StringRedisTemplate
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// 프로덕션 `RoundStateStoreImpl.REVEAL_MILLIS`와 같은 값이어야 한다. 그 상수는 `out`의 private class에
// 갇혀 있어 참조할 수 없으므로 여기서 다시 적는다 — 두 값이 갈리면 아래 두 테스트가 즉시 실패한다.
private const val REVEAL_MILLIS = 10_000L

@IntegrationTest
class RoundStateStoreIntegrationTest(
    @Autowired private val roundStateStore: RoundStateStore,
    @Autowired private val roundService: RoundService,
    @Autowired private val redisTemplate: StringRedisTemplate,
    @Autowired private val playerStep: PlayerStep,
    @Autowired private val playlistStep: PlaylistStep,
    @Autowired private val roomStep: RoomStep,
) {

    private lateinit var player: PlayerResponse
    private lateinit var playlist: PlaylistWithTrackResponse
    private var roomId: Long = 0

    @BeforeEach
    fun setUp() {
        player = playerStep.save(dummyPlayerRequest())
        playlist = playlistStep.save(player, dummyPlaylistCreationRequest())
        roomId = roomStep.save(player, dummyRoomRequest(playlist.id)).id
    }

    @Test
    fun `start_첫 라운드가 OPEN으로 열리고 점수판이 0으로 초기화된다`() {
        roundStateStore.start(roomId, futureSpecs(), setOf(player.id, 100L))

        val snapshot = roundStateStore.snapshot(roomId)!!
        assertThat(snapshot.phase).isEqualTo(RoundPhase.OPEN)
        assertThat(snapshot.roundSeq).isEqualTo(1)
        assertThat(snapshot.scores.map { it.playerId }).containsExactlyInAnyOrder(player.id, 100L)
        assertThat(snapshot.scores.map { it.score }).allMatch { it == 0 }
    }

    @Test
    fun `tryAdvanceOnCorrect_정답이면 REVEAL로 전이되고 승자에게 가점된다`() {
        roundStateStore.start(roomId, futureSpecs(), setOf(player.id))

        val transition = roundStateStore.tryAdvanceOnCorrect(roomId, 1, player.id)

        assertThat(transition.result).isEqualTo(TransitionResult.TRANSITIONED)
        val snapshot = roundStateStore.snapshot(roomId)!!
        assertThat(snapshot.phase).isEqualTo(RoundPhase.REVEAL)
        assertThat(snapshot.winnerId).isEqualTo(player.id)
        assertThat(snapshot.scores.first { it.playerId == player.id }.score).isEqualTo(1)
    }

    @Test
    fun `tryAdvanceOnCorrect_동시 호출에도 단일 winner만 가점된다`() {
        val winners = listOf(1L, 2L, 3L, 4L, 5L)
        roundStateStore.start(roomId, futureSpecs(), winners.toSet())

        val results = runConcurrently(winners.size) { index ->
            roundStateStore.tryAdvanceOnCorrect(roomId, 1, winners[index])
        }

        val transitioned = results.filter { it.result == TransitionResult.TRANSITIONED }
        assertThat(transitioned).hasSize(1)
        val snapshot = roundStateStore.snapshot(roomId)!!
        assertThat(snapshot.phase).isEqualTo(RoundPhase.REVEAL)
        assertThat(snapshot.winnerId).isEqualTo(transitioned.single().winnerId)
        assertThat(snapshot.scores.sumOf { it.score }).isEqualTo(1)
    }

    @Test
    fun `tryAdvanceOnDeadline_동시 호출에도 정확히 한 번만 REVEAL로 전이된다`() {
        roundStateStore.start(roomId, dueSpecs(), setOf(player.id))

        val results = runConcurrently(threads = 5) {
            roundStateStore.tryAdvanceOnDeadline(roomId, 1)
        }

        // sweeper가 경쟁자로 끼어들 수 있으므로 전이의 전역 불변식(epoch가 정확히 1회 전진)을 확인한다.
        val snapshot = roundStateStore.snapshot(roomId)!!
        assertThat(snapshot.roundSeq).isEqualTo(2)
        assertThat(snapshot.phase).isEqualTo(RoundPhase.REVEAL)
        assertThat(results.count { it.result == TransitionResult.TRANSITIONED }).isLessThanOrEqualTo(1)
    }

    @Test
    fun `tryAdvanceOnDeadline_마감 전 조기 발화는 NOT_DUE로 무시된다`() {
        roundStateStore.start(roomId, futureSpecs(), setOf(player.id))

        val transition = roundStateStore.tryAdvanceOnDeadline(roomId, 1)

        assertThat(transition.result).isEqualTo(TransitionResult.NOT_DUE)
        assertThat(roundStateStore.snapshot(roomId)!!.phase).isEqualTo(RoundPhase.OPEN)
    }

    @Test
    fun `tryAdvanceOnCorrect_마감 시각 이후 정답은 거부된다`() {
        roundStateStore.start(roomId, futureSpecs(), setOf(player.id))
        // 마감 시각을 과거로 당기고 sweeper가 닫지 못하도록 ZSET에서 제거해 '마감 후 OPEN 잔여 창'을 재현한다.
        redisTemplate.opsForHash<String, String>().put(RoundRedisKeys.round(roomId), "deadlineAt", "1")
        redisTemplate.opsForZSet().remove(RoundRedisKeys.deadlines(roomId), roomId.toString())

        val transition = roundStateStore.tryAdvanceOnCorrect(roomId, 1, player.id)

        assertThat(transition.result).isEqualTo(TransitionResult.NOT_DUE)
        val snapshot = roundStateStore.snapshot(roomId)!!
        assertThat(snapshot.phase).isEqualTo(RoundPhase.OPEN)
        assertThat(snapshot.scores.first { it.playerId == player.id }.score).isEqualTo(0)
    }

    @Test
    fun `sweepDueRounds_마감 라운드를 REVEAL로 구동한다`() {
        roundService.startRound(roomId)
        // 마감을 과거로 당겨(ZSET 점수·Hash 모두) sweeper가 마감 라운드를 전이하도록 만든다.
        redisTemplate.opsForHash<String, String>().put(RoundRedisKeys.round(roomId), "deadlineAt", "1")
        redisTemplate.opsForZSet().add(RoundRedisKeys.deadlines(roomId), roomId.toString(), 1.0)

        roundService.sweepDueRounds()

        assertThat(roundStateStore.snapshot(roomId)!!.phase).isEqualTo(RoundPhase.REVEAL)
    }

    @Test
    fun `tryAdvanceOnCorrect_REVEAL 마감을 REVEAL_MILLIS 뒤로 잡는다`() {
        roundStateStore.start(roomId, futureSpecs(), setOf(player.id))
        val before = redisNowMillis()

        val transition = roundStateStore.tryAdvanceOnCorrect(roomId, 1, player.id)

        val after = redisNowMillis()
        assertThat(transition.phase).isEqualTo(RoundPhase.REVEAL)
        assertThat(transition.deadlineAt).isBetween(before + REVEAL_MILLIS, after + REVEAL_MILLIS)
    }

    @Test
    fun `tryAdvanceOnDeadline_타임아웃으로 연 REVEAL도 같은 마감을 갖는다`() {
        // 마감이 이미 지난 라운드라 sweeper가 우리보다 먼저 전이할 수 있다. 그래서 우리 호출의 반환값이 아니라
        // 스냅샷을 보고, 기준 시각을 `start` **이전에** 잡는다 — 누가 전이했든 그 시점은 before~after 사이다.
        val before = redisNowMillis()
        roundStateStore.start(roomId, dueSpecs(), setOf(player.id))
        roundStateStore.tryAdvanceOnDeadline(roomId, 1)
        val after = redisNowMillis()

        val snapshot = roundStateStore.snapshot(roomId)!!
        assertThat(snapshot.phase).isEqualTo(RoundPhase.REVEAL)
        assertThat(snapshot.deadlineAt).isBetween(before + REVEAL_MILLIS, after + REVEAL_MILLIS)
    }

    private fun futureSpecs(): List<RoundTrackSpec> =
        listOf(RoundTrackSpec(1L, 60_000), RoundTrackSpec(2L, 60_000))

    private fun dueSpecs(): List<RoundTrackSpec> =
        listOf(RoundTrackSpec(1L, 0), RoundTrackSpec(2L, 0))

    /**
     * 마감은 Lua 안에서 Redis `TIME`으로 계산된다. 테스트 JVM 시계로 재면 컨테이너와의 skew만큼
     * 오차가 생기므로 같은 시계(Redis)로 잰다 — 그래야 허용 오차 없이 정확한 구간 단언이 가능하다.
     */
    private fun redisNowMillis(): Long = redisTemplate.execute(RedisCallback { it.serverCommands().time(TimeUnit.MILLISECONDS) })!!

    private fun runConcurrently(threads: Int, action: (Int) -> RoundTransition): List<RoundTransition> {
        val pool = Executors.newFixedThreadPool(threads)
        val ready = CountDownLatch(threads)
        val go = CountDownLatch(1)
        val results = Collections.synchronizedList(mutableListOf<RoundTransition>())
        val futures = (0 until threads).map { index ->
            pool.submit {
                ready.countDown()
                go.await()
                results.add(action(index))
            }
        }
        ready.await()
        go.countDown()
        futures.forEach { it.get(5, TimeUnit.SECONDS) }
        pool.shutdown()
        return results.toList()
    }
}
