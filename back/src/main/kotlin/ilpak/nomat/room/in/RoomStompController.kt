package ilpak.nomat.room.`in`

import com.fasterxml.jackson.databind.ObjectMapper
import ilpak.nomat.infrastructure.web.JwtHandshakeInterceptor
import ilpak.nomat.infrastructure.web.RoomJoinChannelInterceptor
import ilpak.nomat.player.application.PlayerService
import ilpak.nomat.room.application.RoomService
import ilpak.nomat.room.application.dto.RoomChatEventMessage
import ilpak.nomat.room.application.dto.RoomChatRequest
import jakarta.validation.Valid
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.stereotype.Controller
import java.time.Instant

@Controller
private class RoomStompController(
    private val roomService: RoomService,
    private val playerService: PlayerService,
    private val objectMapper: ObjectMapper,
    private val redisTemplate: StringRedisTemplate,
) {

    @MessageMapping("/rooms/leave")
    fun leave(headerAccessor: SimpMessageHeaderAccessor) {
        val playerId = headerAccessor.sessionAttributes?.get(JwtHandshakeInterceptor.PLAYER_ID_KEY) as? Long ?: return
        val roomId = headerAccessor.sessionAttributes?.get(RoomJoinChannelInterceptor.ROOM_ID_KEY) as? Long ?: return
        roomService.leave(roomId, playerId)
    }

    @MessageMapping("/rooms/chat")
    fun chat(@Valid @Payload request: RoomChatRequest, headerAccessor: SimpMessageHeaderAccessor) {
        val playerId = headerAccessor.sessionAttributes?.get(JwtHandshakeInterceptor.PLAYER_ID_KEY) as? Long ?: return
        val roomId = headerAccessor.sessionAttributes?.get(RoomJoinChannelInterceptor.ROOM_ID_KEY) as? Long ?: return
        val player = playerService.findById(playerId)

        val event = RoomChatEventMessage(
            roomId = roomId,
            playerId = playerId,
            nickname = player.nickname,
            content = request.content,
            timestamp = Instant.now(),
        )
        val channel = "room:${roomId}:events"
        redisTemplate.convertAndSend(channel, objectMapper.writeValueAsString(event))
    }
}
