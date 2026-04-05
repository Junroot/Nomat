package ilpak.nomat.room.`in`

import com.fasterxml.jackson.databind.ObjectMapper
import ilpak.nomat.player.application.PlayerService
import ilpak.nomat.room.application.domain.RoomJoinedEvent
import ilpak.nomat.room.application.domain.RoomLeftEvent
import ilpak.nomat.room.application.dto.RoomEventMessage
import ilpak.nomat.room.application.dto.RoomJoinedEventMessage
import ilpak.nomat.room.application.dto.RoomLeftEventMessage
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
private class RoomEventListener(
    private val playerService: PlayerService,
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleRoomJoined(event: RoomJoinedEvent) {
        val player = playerService.findById(event.playerId)
        val channel = RoomEventMessage.channelFor(event.roomId)
        val message = objectMapper.writeValueAsString(
            RoomJoinedEventMessage(
                roomId = event.roomId,
                playerId = event.playerId,
                nickname = player.nickname,
            )
        )
        redisTemplate.convertAndSend(channel, message)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleRoomLeft(event: RoomLeftEvent) {
        val player = playerService.findById(event.playerId)
        val channel = RoomEventMessage.channelFor(event.roomId)
        val message = objectMapper.writeValueAsString(
            RoomLeftEventMessage(
                roomId = event.roomId,
                playerId = event.playerId,
                nickname = player.nickname,
            )
        )
        redisTemplate.convertAndSend(channel, message)
    }
}
