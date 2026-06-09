package ilpak.nomat.room.`in`

import com.fasterxml.jackson.databind.ObjectMapper
import ilpak.nomat.infrastructure.web.JwtHandshakeInterceptor
import ilpak.nomat.infrastructure.web.RoomJoinChannelInterceptor
import ilpak.nomat.room.application.RoomService
import ilpak.nomat.room.application.dto.RoomChatEventMessage
import ilpak.nomat.room.application.dto.RoomChatRequest
import ilpak.nomat.room.application.dto.RoomEventMessage
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.stereotype.Controller
import java.time.Instant

private val log = LoggerFactory.getLogger(RoomStompController::class.java)

@Controller
private class RoomStompController(
    private val roomService: RoomService,
    private val objectMapper: ObjectMapper,
    private val redisTemplate: StringRedisTemplate,
) {

    @MessageMapping("/rooms/leave")
    fun leave(headerAccessor: SimpMessageHeaderAccessor) {
        val session = headerAccessor.roomSession() ?: return
        roomService.leave(session.roomId, session.playerId)
    }

    @MessageMapping("/rooms/start")
    fun start(headerAccessor: SimpMessageHeaderAccessor) {
        val session = headerAccessor.roomSession() ?: return
        roomService.start(session.roomId, session.playerId)
    }

    @MessageMapping("/rooms/end")
    fun end(headerAccessor: SimpMessageHeaderAccessor) {
        val session = headerAccessor.roomSession() ?: return
        roomService.end(session.roomId, session.playerId)
    }

    @MessageMapping("/rooms/chat")
    fun chat(@Valid @Payload request: RoomChatRequest, headerAccessor: SimpMessageHeaderAccessor) {
        val session = headerAccessor.roomSession() ?: return

        val event = RoomChatEventMessage(
            roomId = session.roomId,
            playerId = session.playerId,
            nickname = session.nickname,
            content = request.content,
            timestamp = Instant.now(),
        )
        val channel = RoomEventMessage.channelFor(session.roomId)
        redisTemplate.convertAndSend(channel, objectMapper.writeValueAsString(event))
    }
}

private data class RoomSession(val playerId: Long, val roomId: Long, val nickname: String)

private fun SimpMessageHeaderAccessor.roomSession(): RoomSession? {
    val attrs = sessionAttributes ?: return null
    val playerId = attrs[JwtHandshakeInterceptor.PLAYER_ID_KEY] as? Long ?: return null
    val roomId = attrs[RoomJoinChannelInterceptor.ROOM_ID_KEY] as? Long ?: return null
    val nickname = attrs[RoomJoinChannelInterceptor.NICKNAME_KEY] as? String ?: return null
    return RoomSession(playerId, roomId, nickname)
}
