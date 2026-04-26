package ilpak.nomat.infrastructure.redis

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.listener.RedisMessageListenerContainer

@Configuration
@EnableConfigurationProperties(RedisPubSubHealthProperties::class)
private class RedisListenerContainerConfiguration {

    @Bean
    fun redisMessageListenerContainer(
        connectionFactory: RedisConnectionFactory,
    ): RedisMessageListenerContainer {
        val container = RedisMessageListenerContainer()
        container.setConnectionFactory(connectionFactory)
        return container
    }
}
