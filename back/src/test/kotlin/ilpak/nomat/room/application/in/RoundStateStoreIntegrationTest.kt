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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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


    @ParameterizedTest
    @CsvSource("1, 1", "2, 2", "3, 2", "4, 3", "5, 4", "8, 6", "20, 14")
    fun `togglePass_남은 인원별 필요 포기 인원은 2 3의 올림이다`(remaining: Int, required: Int) {
        // 남은 인원 1명은 `1*3 >= 1*2`가 참이라 즉시 전이된다 — 혼자면 혼자 결정한다(의도된 경계).
        val room = SYNTHETIC_ROOM_ID + remaining
        val players = (1L..remaining.toLong()).toList()
        roundStateStore.start(room, futureSpecs(), players.toSet())

        val outcomes = players.take(required).map { roundStateStore.togglePass(room, 1, it) }

        assertThat(outcomes.dropLast(1)).allSatisfy {
            assertThat(it.transition.result).isEqualTo(TransitionResult.IGNORED)
            assertThat(it.requiredCount).isEqualTo(required)
        }
        assertThat(outcomes.last().transition.result).isEqualTo(TransitionResult.TRANSITIONED)
        val snapshot = roundStateStore.snapshot(room)!!
        assertThat(snapshot.phase).isEqualTo(RoundPhase.REVEAL)
        assertThat(snapshot.winnerId).isNull()
    }

    @Test
    fun `togglePass_같은 참가자가 두 번 보내면 포기가 해제된다`() {
        roundStateStore.start(roomId, futureSpecs(), setOf(1L, 2L, 3L, 4L, 5L))

        val on = roundStateStore.togglePass(roomId, 1, 1L)
        val off = roundStateStore.togglePass(roomId, 1, 1L)

        assertThat(on.passing).isTrue()
        assertThat(on.passedCount).isEqualTo(1)
        assertThat(off.passing).isFalse()
        assertThat(off.passedCount).isEqualTo(0)
        assertThat(roundStateStore.snapshot(roomId)!!.phase).isEqualTo(RoundPhase.OPEN)
    }

    @Test
    fun `togglePass_이전 라운드를 가리키는 포기는 무시된다`() {
        roundStateStore.start(roomId, futureSpecs(), setOf(1L, 2L, 3L))
        roundStateStore.tryAdvanceOnCorrect(roomId, 1, 1L)

        val outcome = roundStateStore.togglePass(roomId, 1, 2L)

        assertThat(outcome.transition.result).isEqualTo(TransitionResult.IGNORED)
        assertThat(outcome.passedCount).isEqualTo(0)
    }

    @Test
    fun `togglePass_라운드가 바뀌면 이전 라운드의 포기 집합이 폐기된다`() {
        roundStateStore.start(roomId, futureSpecs(), setOf(1L, 2L, 3L))
        roundStateStore.togglePass(roomId, 1, 1L)
        // REVEAL을 거쳐 다음 OPEN(roundSeq=3)까지 진행시킨다. 전이 스크립트는 포기 집합을 지우지 않는다.
        roundStateStore.tryAdvanceOnCorrect(roomId, 1, 1L)
        expireRound()
        roundStateStore.tryAdvanceOnDeadline(roomId, 2)
        assertThat(redisTemplate.opsForSet().size(RoundRedisKeys.passes(roomId))).isEqualTo(1)

        val outcome = roundStateStore.togglePass(roomId, 3, 2L)

        // lazy reset이 잔재를 폐기했으므로 새 라운드의 포기 인원은 방금 누른 1명뿐이다.
        assertThat(outcome.passedCount).isEqualTo(1)
        assertThat(redisTemplate.opsForSet().members(RoundRedisKeys.passes(roomId))).containsExactly("2")
    }

    @Test
    fun `togglePass_마감 시각 이후 도착한 포기는 거부된다`() {
        roundStateStore.start(roomId, futureSpecs(), setOf(1L))
        // 마감 시각을 과거로 당기고 sweeper가 닫지 못하도록 ZSET에서 제거해 '마감 후 OPEN 잔여 창'을 재현한다.
        redisTemplate.opsForHash<String, String>().put(RoundRedisKeys.round(roomId), "deadlineAt", "1")
        redisTemplate.opsForZSet().remove(RoundRedisKeys.deadlines(roomId), roomId.toString())

        val outcome = roundStateStore.togglePass(roomId, 1, 1L)

        assertThat(outcome.transition.result).isEqualTo(TransitionResult.NOT_DUE)
        assertThat(roundStateStore.snapshot(roomId)!!.phase).isEqualTo(RoundPhase.OPEN)
        assertThat(redisTemplate.hasKey(RoundRedisKeys.passes(roomId))).isFalse()
    }

    @Test
    fun `togglePass_동시에 임계를 넘겨도 전이는 정확히 한 번만 일어난다`() {
        val players = listOf(1L, 2L, 3L, 4L, 5L)
        roundStateStore.start(roomId, futureSpecs(), players.toSet())

        val results = runConcurrently(players.size) { index ->
            roundStateStore.togglePass(roomId, 1, players[index]).transition
        }

        assertThat(results.count { it.result == TransitionResult.TRANSITIONED }).isEqualTo(1)
        val snapshot = roundStateStore.snapshot(roomId)!!
        assertThat(snapshot.roundSeq).isEqualTo(2)
        assertThat(snapshot.phase).isEqualTo(RoundPhase.REVEAL)
    }

    @Test
    fun `togglePass_정답과 동시에 발화해도 라운드는 한 번만 전이된다`() {
        val players = listOf(1L, 2L, 3L)
        roundStateStore.start(roomId, futureSpecs(), players.toSet())
        // 임계(2명) 직전까지 채워, 마지막 포기와 정답이 같은 순간에 전이를 시도하게 만든다.
        roundStateStore.togglePass(roomId, 1, 1L)

        val results = runConcurrently(threads = 2) { index ->
            if (index == 0) {
                roundStateStore.togglePass(roomId, 1, 2L).transition
            } else {
                roundStateStore.tryAdvanceOnCorrect(roomId, 1, 3L)
            }
        }

        assertThat(results.count { it.result == TransitionResult.TRANSITIONED }).isEqualTo(1)
        val snapshot = roundStateStore.snapshot(roomId)!!
        assertThat(snapshot.roundSeq).isEqualTo(2)
        assertThat(snapshot.phase).isEqualTo(RoundPhase.REVEAL)
        assertThat(snapshot.scores.sumOf { it.score }).isLessThanOrEqualTo(1)
    }

    @Test
    fun `onPlayerLeft_미포기자가 나가 임계가 내려가면 전이된다`() {
        val players = listOf(1L, 2L, 3L, 4L, 5L)
        roundStateStore.start(roomId, futureSpecs(), players.toSet())
        // 5명/임계 4/포기 3 — 아무도 새로 누르지 않았지만 한 명이 나가면 4명/임계 3이 되어 도달한다.
        listOf(1L, 2L, 3L).forEach { roundStateStore.togglePass(roomId, 1, it) }
        assertThat(roundStateStore.snapshot(roomId)!!.phase).isEqualTo(RoundPhase.OPEN)

        val outcome = roundStateStore.onPlayerLeft(roomId, 5L)

        assertThat(outcome.transition.result).isEqualTo(TransitionResult.TRANSITIONED)
        val snapshot = roundStateStore.snapshot(roomId)!!
        assertThat(snapshot.phase).isEqualTo(RoundPhase.REVEAL)
        assertThat(snapshot.winnerId).isNull()
    }

    @Test
    fun `onPlayerLeft_포기자가 나가면 포기 인원수도 함께 줄어든다`() {
        val players = listOf(1L, 2L, 3L, 4L, 5L)
        roundStateStore.start(roomId, futureSpecs(), players.toSet())
        listOf(1L, 2L).forEach { roundStateStore.togglePass(roomId, 1, it) }

        val outcome = roundStateStore.onPlayerLeft(roomId, 1L)

        // 분자가 분모를 넘지 않아야 한다 — 나간 포기자를 집합에 남기면 4명 중 2명이 아니라 3명이 된다.
        assertThat(outcome.passedCount).isEqualTo(1)
        assertThat(outcome.requiredCount).isEqualTo(3)
        assertThat(roundStateStore.snapshot(roomId)!!.phase).isEqualTo(RoundPhase.OPEN)
    }

    @Test
    fun `onPlayerLeft_퇴장 후에도 임계에 미달이면 라운드가 유지된다`() {
        val players = listOf(1L, 2L, 3L, 4L, 5L)
        roundStateStore.start(roomId, futureSpecs(), players.toSet())
        roundStateStore.togglePass(roomId, 1, 1L)

        val outcome = roundStateStore.onPlayerLeft(roomId, 5L)

        assertThat(outcome.transition.result).isEqualTo(TransitionResult.IGNORED)
        assertThat(outcome.passedCount).isEqualTo(1)
        assertThat(outcome.requiredCount).isEqualTo(3)
        assertThat(roundStateStore.snapshot(roomId)!!.phase).isEqualTo(RoundPhase.OPEN)
    }

    @Test
    fun `isPassing_라운드가 바뀌면 이전 라운드의 포기는 유효하지 않다`() {
        roundStateStore.start(roomId, futureSpecs(), setOf(1L, 2L, 3L))
        roundStateStore.togglePass(roomId, 1, 1L)
        assertThat(roundStateStore.isPassing(roomId, 1L)).isTrue()

        roundStateStore.tryAdvanceOnCorrect(roomId, 1, 2L)

        // `passes`에 1L이 남아 있어도 `passSeq != roundSeq`라 잔재로 취급해야 한다.
        assertThat(redisTemplate.opsForSet().members(RoundRedisKeys.passes(roomId))).containsExactly("1")
        assertThat(roundStateStore.isPassing(roomId, 1L)).isFalse()
    }

    @Test
    fun `start_이전 게임의 포기 상태는 새 게임으로 이월되지 않는다`() {
        roundStateStore.start(roomId, futureSpecs(), setOf(1L, 2L, 3L))
        roundStateStore.togglePass(roomId, 1, 1L)

        // 게임이 자연 종료(ENDED)되는 경로에는 teardown이 없어 포기 집합이 TTL로 살아남는다.
        // 재시작하면 roundSeq가 1로 되돌아가 lazy reset 판별식이 잔재를 "유효"로 읽는다.
        roundStateStore.start(roomId, futureSpecs(), setOf(1L, 2L, 3L))

        val snapshot = roundStateStore.snapshot(roomId, 1L)!!
        assertThat(snapshot.roundSeq).isEqualTo(1)
        assertThat(snapshot.passedCount).isEqualTo(0)
        assertThat(snapshot.passing).isFalse()
        assertThat(roundStateStore.isPassing(roomId, 1L)).isFalse()
    }

    @Test
    fun `snapshot_포기 현황은 조회자 기준으로 채워진다`() {
        roundStateStore.start(roomId, futureSpecs(), setOf(1L, 2L, 3L, 4L))
        roundStateStore.togglePass(roomId, 1, 1L)

        assertThat(roundStateStore.snapshot(roomId, 1L)!!.passing).isTrue()
        val other = roundStateStore.snapshot(roomId, 2L)!!
        assertThat(other.passing).isFalse()
        assertThat(other.passedCount).isEqualTo(1)
        assertThat(other.requiredCount).isEqualTo(3)
    }

    /** 마감을 과거로 당겨 다음 `tryAdvanceOnDeadline`이 곧바로 전이하도록 만든다. */
    private fun expireRound() {
        redisTemplate.opsForHash<String, String>().put(RoundRedisKeys.round(roomId), "deadlineAt", "1")
    }

    private fun futureSpecs(): List<RoundTrackSpec> =
        listOf(RoundTrackSpec(1L, 60_000), RoundTrackSpec(2L, 60_000))

    private fun dueSpecs(): List<RoundTrackSpec> =
        listOf(RoundTrackSpec(1L, 0), RoundTrackSpec(2L, 0))

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

    companion object {
        // 임계 판정마다 새 라운드가 필요해 DB 방 없이 라운드 상태만 여는 합성 roomId를 쓴다
        // (`유령 방은 sweeper가 정리한다` 테스트와 같은 방식). Redis는 테스트마다 flush된다.
        private const val SYNTHETIC_ROOM_ID = 9_000_000L
    }
}
