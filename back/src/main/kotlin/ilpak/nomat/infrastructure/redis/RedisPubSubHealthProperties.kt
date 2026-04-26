package ilpak.nomat.infrastructure.redis

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.health.pubsub")
data class RedisPubSubHealthProperties(
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    val channelPrefix: String = DEFAULT_CHANNEL_PREFIX,
) {
    companion object {
        const val DEFAULT_TIMEOUT_MS: Long = 2_000
        const val DEFAULT_CHANNEL_PREFIX: String = "health:pubsub"
    }
}
