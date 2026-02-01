package ilpak.nomat.room.out

import ilpak.nomat.room.application.domain.RoomEntries
import ilpak.nomat.room.application.domain.RoomEntry
import ilpak.nomat.room.application.domain.RoomEntryRepository
import ilpak.nomat.room.application.domain.RoomEntryResult
import org.springframework.data.redis.connection.DefaultStringRedisConnection
import org.springframework.data.redis.connection.RedisConnection
import org.springframework.data.redis.core.RedisCallback
import org.springframework.data.redis.core.RedisOperations
import org.springframework.data.redis.core.SessionCallback
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ZSetOperations
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.collections.map

@Repository
private class RoomEntryRepositoryImpl(
    private val redisTemplate: StringRedisTemplate,
) : RoomEntryRepository {

    override fun tryEnter(
        roomId: Long,
        playerId: Long,
        limit: Int
    ): RoomEntryResult {
        val resultCode = redisTemplate.execute(
            ENTER_SCRIPT,
            listOf("room::$roomId::entry"),
            playerId.toString(),
            limit.toString(),
            System.currentTimeMillis().toString()
        )

        return RoomEntryResult.fromCode(resultCode)
            ?: throw IllegalStateException("Unexpected result code: $resultCode")
    }

    override fun getEntries(roomId: Long): RoomEntries {
        return getEntries(listOf(roomId)).getOrElse(roomId) { RoomEntries(emptyList()) }
    }

    override fun getEntries(roomIds: Collection<Long>): Map<Long, RoomEntries> {
        if (roomIds.isEmpty()) {
            return emptyMap()
        }

        // Pipeline을 사용하여 효율적으로 다건 조회
        val results = redisTemplate.executePipelined(
            object : SessionCallback<Any> {
                override fun <K, V> execute(operations: RedisOperations<K, V>): Any? {
                    @Suppress("UNCHECKED_CAST")
                    val stringOps = operations as RedisOperations<String, String>

                    roomIds.forEach { roomId ->
                        stringOps.opsForZSet().rangeWithScores("room::$roomId::entry", 0L, Long.MAX_VALUE)
                    }
                    return null
                }
            })

        return roomIds.mapIndexed { index, roomId ->
            @Suppress("UNCHECKED_CAST")
            val tuples = results[index] as? Set<ZSetOperations.TypedTuple<String>>

            val entries = tuples
                ?.map { tuple ->
                    RoomEntry(
                        tuple.value?.toLongOrNull() ?: throw IllegalArgumentException("Unknown tuple: $tuple"),
                        tuple.score?.toLong()
                            ?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
                            ?: throw IllegalArgumentException("Unknown score: $tuple")
                    )
                }
                ?: emptyList()

            roomId to RoomEntries(entries)
        }.toMap()
    }

    companion object {
        private val ENTER_SCRIPT = RedisScript.of(
            """
            local key = KEYS[1]
            local playerId = ARGV[1]
            local limit = tonumber(ARGV[2])
            local score = tonumber(ARGV[3])
    
            if redis.call('ZSCORE', key, playerId) then return 1 end
            if redis.call('ZCARD', key) >= limit then return 2 end
            
            redis.call('ZADD', key, score, playerId)
            return 0
            """.trimIndent(),
            Int::class.java
        )
    }
}
