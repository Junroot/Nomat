package ilpak.nomat.infrastructure.events

import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory

@Configuration
class ShedLockConfiguration {

    @Bean
    fun lockProvider(redisConnectionFactory: RedisConnectionFactory): LockProvider =
        RedisLockProvider.Builder(redisConnectionFactory)
            .keyPrefix("shedlock")
            .environment("nomat")
            .build()
}
