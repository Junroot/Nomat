package ilpak.nomat.infrastructure.redis

import ilpak.nomat.common.exception.ConflictException
import ilpak.nomat.common.lock.DistributedLockExecutor
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

@Service
class RedisDistributedLockExecutor(
    private val redisTemplate: StringRedisTemplate,
) : DistributedLockExecutor {

    override fun <T> withLock(key: String, action: () -> T): T {
        val lockValue = UUID.randomUUID().toString()
        var acquired = false

        for (i in 0 until MAX_RETRY_COUNT) {
            acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, lockValue, Duration.ofSeconds(LOCK_TIMEOUT_SECONDS))
                ?: false
            if (acquired) break
            Thread.sleep(RETRY_INTERVAL_MS)
        }

        if (!acquired) {
            throw ConflictException("다른 요청이 처리 중입니다. 잠시 후 다시 시도해주세요.")
        }

        try {
            return action()
        } finally {
            val currentValue = redisTemplate.opsForValue().get(key)
            if (lockValue == currentValue) {
                redisTemplate.delete(key)
            }
        }
    }

    companion object {
        private const val LOCK_TIMEOUT_SECONDS = 5L
        private const val MAX_RETRY_COUNT = 50
        private const val RETRY_INTERVAL_MS = 100L
    }
}
