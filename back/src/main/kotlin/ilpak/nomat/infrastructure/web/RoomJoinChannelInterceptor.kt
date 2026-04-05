package ilpak.nomat.infrastructure.web

import ilpak.nomat.common.exception.BadRequestException
import ilpak.nomat.player.application.PlayerService
import ilpak.nomat.room.application.RoomService
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

        if (!reconnectGracePeriodManager.cancelGracePeriod(roomId, playerId)) {
            roomService.join(roomId, playerId, password)
        }

        val player = playerService.findById(playerId)
        accessor.sessionAttributes?.set(ROOM_ID_KEY, roomId)
        accessor.sessionAttributes?.set(NICKNAME_KEY, player.nickname)
        return message
    }

    companion object {
        const val ROOM_ID_KEY = "roomId"
        const val NICKNAME_KEY = "nickname"
        private const val ROOM_ID_HEADER = "roomId"
        private const val PASSWORD_HEADER = "password"
    }
}
