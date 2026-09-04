package ilpak.nomat.room.application.`in`

import ilpak.nomat.infrastructure.integration.IntegrationTest
import ilpak.nomat.room.application.domain.PendingLeave
import ilpak.nomat.room.application.domain.PendingLeaveStore
import ilpak.nomat.room.out.PendingLeaveRedisKeys
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration

@IntegrationTest
class PendingLeaveStoreIntegrationTest(
    @Autowired private val pendingLeaveStore: PendingLeaveStore,
    @Autowired private val redisTemplate: StringRedisTemplate,
) {

    // 다른 테스트의 끊김 예약과 섞이지 않도록 존재하지 않는 큰 roomId를 쓴다.
    private val roomId = 9_000_000L
    private val playerId = 42L

    @BeforeEach
    fun setUp() {
        redisTemplate.opsForZSet().remove(PendingLeaveRedisKeys.PENDING_LEAVES, PendingLeaveRedisKeys.member(roomId, playerId))
    }

    @Test
    fun `schedule_유예가 지나기 전에는 due가 아니고 지나면 due가 된다`() {
        pendingLeaveStore.schedule(roomId, playerId, 1)

        assertThat(pendingLeaveStore.findDue()).doesNotContain(PendingLeave(roomId, playerId))

        await()
            .pollInterval(Duration.ofMillis(200))
            .atMost(Duration.ofSeconds(3))
            .untilAsserted {
                assertThat(pendingLeaveStore.findDue()).contains(PendingLeave(roomId, playerId))
            }
    }

    @Test
    fun `remove_예약이 있었을 때만 true를 돌려주고 두 번째는 false다`() {
        pendingLeaveStore.schedule(roomId, playerId, 60)

        assertThat(pendingLeaveStore.remove(roomId, playerId)).isTrue()
        assertThat(pendingLeaveStore.remove(roomId, playerId)).isFalse()
        assertThat(pendingLeaveStore.findDue()).doesNotContain(PendingLeave(roomId, playerId))
    }

    @Test
    fun `restore_복원한 항목은 즉시 due가 아니고 재시도 간격 뒤에 due가 된다`() {
        pendingLeaveStore.schedule(roomId, playerId, 0)
        assertThat(pendingLeaveStore.remove(roomId, playerId)).isTrue()

        pendingLeaveStore.restore(roomId, playerId)

        assertThat(pendingLeaveStore.findDue()).doesNotContain(PendingLeave(roomId, playerId))
        await()
            .pollInterval(Duration.ofMillis(500))
            .atMost(Duration.ofSeconds(8))
            .untilAsserted {
                assertThat(pendingLeaveStore.findDue()).contains(PendingLeave(roomId, playerId))
            }
    }

    @Test
    fun `member 포맷은 roomId와 playerId로 왕복된다`() {
        assertThat(PendingLeaveRedisKeys.parseMember(PendingLeaveRedisKeys.member(roomId, playerId)))
            .isEqualTo(roomId to playerId)
        assertThat(PendingLeaveRedisKeys.parseMember("garbage")).isNull()
    }
}
