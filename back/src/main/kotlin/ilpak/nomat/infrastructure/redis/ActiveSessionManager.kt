package ilpak.nomat.infrastructure.redis

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class ActiveSessionManager(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {

    fun getSession(playerId: Long): SessionInfo? {
        val value = redisTemplate.opsForValue().get(keyFor(playerId)) ?: return null
        return objectMapper.readValue(value, SessionInfo::class.java)
    }

    fun setSession(playerId: Long, sessionId: String, roomId: Long) {
        val value = objectMapper.writeValueAsString(SessionInfo(sessionId, roomId))
        redisTemplate.opsForValue().set(keyFor(playerId), value, SESSION_TTL)
    }

    fun isActiveSession(playerId: Long, sessionId: String): Boolean {
        val session = getSession(playerId) ?: return false
        return session.sessionId == sessionId
    }

    fun removeSession(playerId: Long, sessionId: String): Boolean {
        val result = redisTemplate.execute(
            REMOVE_SESSION_SCRIPT,
            listOf(keyFor(playerId)),
            sessionId,
        )
        return result == 1L
    }

    private fun keyFor(playerId: Long): String = "$KEY_PREFIX$playerId"

    companion object {
        private const val KEY_PREFIX = "player:session:"
        private val SESSION_TTL = Duration.ofHours(24)

        private val REMOVE_SESSION_SCRIPT = DefaultRedisScript<Long>(
            """
            local value = redis.call('GET', KEYS[1])
            if value then
                local session = cjson.decode(value)
                if session['sessionId'] == ARGV[1] then
                    redis.call('DEL', KEYS[1])
                    return 1
                end
            end
            return 0
            """.trimIndent(),
            Long::class.javaObjectType,
        )
    }
}

data class SessionInfo(
    val sessionId: String,
    val roomId: Long,
)
