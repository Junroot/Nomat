package ilpak.nomat.infrastructure.redis

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.health.AbstractHealthIndicator
import org.springframework.boot.actuate.health.Health
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private val log = LoggerFactory.getLogger(RedisPubSubHealthIndicator::class.java)

@Component
private class RedisPubSubHealthIndicator(
    private val redisTemplate: StringRedisTemplate,
    private val listenerContainer: RedisMessageListenerContainer,
    private val properties: RedisPubSubHealthProperties,
) : AbstractHealthIndicator(), MessageListener {

    private val instanceId: String = UUID.randomUUID().toString()
    private val channel: String = "${properties.channelPrefix}:$instanceId"
    private val pendingPings: ConcurrentHashMap<String, CompletableFuture<Unit>> = ConcurrentHashMap()

    @PostConstruct
    fun registerListener() {
        listenerContainer.addMessageListener(this, ChannelTopic(channel))
    }

    override fun doHealthCheck(builder: Health.Builder) {
        val payload = "${System.nanoTime()}-${UUID.randomUUID()}"
        val future = CompletableFuture<Unit>()
        pendingPings[payload] = future
        val startNs = System.nanoTime()
        try {
            redisTemplate.convertAndSend(channel, payload)
            future.get(properties.timeoutMs, TimeUnit.MILLISECONDS)
            val latencyMs = (System.nanoTime() - startNs) / NANOS_PER_MILLI
            builder.up()
                .withDetail("channel", channel)
                .withDetail("latencyMs", latencyMs)
        } catch (ex: TimeoutException) {
            log.warn(
                "Redis pub/sub round-trip timed out after {}ms (channel={})",
                properties.timeoutMs,
                channel,
            )
            builder.down()
                .withDetail("channel", channel)
                .withDetail("timeoutMs", properties.timeoutMs)
                .withDetail("reason", "expected ping not received")
        } catch (ex: Exception) {
            log.warn(
                "Redis pub/sub round-trip failed (channel={}): {}",
                channel,
                ex.message,
            )
            builder.down()
                .withDetail("channel", channel)
                .withDetail("reason", "publish or subscribe failure")
        } finally {
            pendingPings.remove(payload)
        }
    }

    override fun onMessage(message: Message, pattern: ByteArray?) {
        val payload = String(message.body)
        pendingPings[payload]?.complete(Unit)
    }

    companion object {
        private const val NANOS_PER_MILLI: Long = 1_000_000
    }
}
