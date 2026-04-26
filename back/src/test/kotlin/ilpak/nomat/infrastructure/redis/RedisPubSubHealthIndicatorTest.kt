package ilpak.nomat.infrastructure.redis

import ilpak.nomat.infrastructure.integration.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.actuate.health.Status
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration
import java.util.UUID

@IntegrationTest
class RedisPubSubHealthIndicatorTest(
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("redisPubSubHealthIndicator")
    private val healthIndicator: HealthIndicator,
    @Autowired private val redisTemplate: StringRedisTemplate,
) {

    @Test
    fun `health_정상 round-trip 시 UP과 latencyMs를 반환한다`() {
        val health = healthIndicator.health()

        assertThat(health.status).isEqualTo(Status.UP)
        assertThat(health.details).containsKey("channel")
        assertThat(health.details).containsKey("latencyMs")

        val channel = health.details["channel"] as String
        assertThat(channel).startsWith("health:pubsub:")
        val instanceId = channel.removePrefix("health:pubsub:")
        UUID.fromString(instanceId)
    }

    @Test
    fun `health_채널은 인스턴스별 UUID로 구성되어 다른 인스턴스의 ping과 분리된다`() {
        val health = healthIndicator.health()
        val channel = health.details["channel"] as String

        val foreignChannel = "health:pubsub:${UUID.randomUUID()}"
        assertThat(channel).isNotEqualTo(foreignChannel)

        // 외부에서 자신의 채널에 가짜 payload를 흘려도 자신의 새 ping은 정상으로 받는다.
        redisTemplate.convertAndSend(channel, "foreign-payload-${UUID.randomUUID()}")

        await().pollDelay(Duration.ofMillis(100)).atMost(Duration.ofSeconds(2)).untilAsserted {
            val nextHealth = healthIndicator.health()
            assertThat(nextHealth.status).isEqualTo(Status.UP)
            assertThat(nextHealth.details).containsKey("latencyMs")
        }
    }
}
