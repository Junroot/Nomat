package ilpak.nomat.infrastructure.web

import com.fasterxml.jackson.databind.ObjectMapper
import ilpak.nomat.common.exception.BadRequestException
import ilpak.nomat.infrastructure.redis.ActiveSessionManager
import ilpak.nomat.player.application.PlayerService
import ilpak.nomat.room.application.RoomService
import ilpak.nomat.room.application.dto.RoomEventMessage
import ilpak.nomat.room.application.dto.SessionReplacedEventMessage
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.stereotype.Component

@Component
class RoomJoinChannelInterceptor(
    private val roomService: RoomService,
    private val playerService: PlayerService,
    private val reconnectGracePeriodManager: ReconnectGracePeriodManager,
    private val activeSessionManager: ActiveSessionManager,
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) : ChannelInterceptor {

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
        val accessor = StompHeaderAccessor.wrap(message)
        if (accessor.command != StompCommand.CONNECT) {
            return message
        }

        val roomId = accessor.getFirstNativeHeader(ROOM_ID_HEADER)?.toLongOrNull()
            ?: throw BadRequestException("roomId 헤더가 필요합니다.")
        val password = accessor.getFirstNativeHeader(PASSWORD_HEADER)
        val playerId = accessor.sessionAttributes?.get(JwtHandshakeInterceptor.PLAYER_ID_KEY) as? Long
            ?: throw BadRequestException("인증 정보가 없습니다.")
        val sessionId = accessor.sessionId
            ?: throw BadRequestException("세션 정보가 없습니다.")
        val player = playerService.findById(playerId)

        val existingSession = activeSessionManager.getSession(playerId)
        if (existingSession != null) {
            if (existingSession.roomId == roomId) {
                reconnectGracePeriodManager.cancelGracePeriod(roomId, playerId)
            } else {
                reconnectGracePeriodManager.cancelGracePeriod(existingSession.roomId, playerId)
                roomService.leave(existingSession.roomId, playerId)
                roomService.join(roomId, playerId, password)
            }

            if (existingSession.sessionId != sessionId) {
                val event = SessionReplacedEventMessage(
                    roomId = existingSession.roomId,
                    playerId = playerId,
                    nickname = player.nickname,
                )
                val eventChannel = RoomEventMessage.channelFor(existingSession.roomId)
                redisTemplate.convertAndSend(eventChannel, objectMapper.writeValueAsString(event))
            }
        } else {
            if (!reconnectGracePeriodManager.cancelGracePeriod(roomId, playerId)) {
                roomService.join(roomId, playerId, password)
            }
        }

        activeSessionManager.setSession(playerId, sessionId, roomId)

        accessor.sessionAttributes?.set(ROOM_ID_KEY, roomId)
        accessor.sessionAttributes?.set(NICKNAME_KEY, player.nickname)
        accessor.sessionAttributes?.set(SESSION_ID_KEY, sessionId)
        return message
    }

    companion object {
        const val ROOM_ID_KEY = "roomId"
        const val NICKNAME_KEY = "nickname"
        const val SESSION_ID_KEY = "sessionId"
        private const val ROOM_ID_HEADER = "roomId"
        private const val PASSWORD_HEADER = "password"
    }
}
