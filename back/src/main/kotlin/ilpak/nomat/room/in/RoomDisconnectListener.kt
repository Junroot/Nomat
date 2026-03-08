package ilpak.nomat.room.`in`

import ilpak.nomat.infrastructure.web.JwtHandshakeInterceptor
import ilpak.nomat.infrastructure.web.ReconnectGracePeriodManager
import ilpak.nomat.infrastructure.web.RoomJoinChannelInterceptor
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.web.socket.messaging.SessionDisconnectEvent

@Component
private class RoomDisconnectListener(
    private val reconnectGracePeriodManager: ReconnectGracePeriodManager,
) {

    @EventListener
    fun handleSessionDisconnect(event: SessionDisconnectEvent) {
        val sessionAttributes = event.message.headers["simpSessionAttributes"] as? Map<*, *> ?: return
        val playerId = sessionAttributes[JwtHandshakeInterceptor.PLAYER_ID_KEY] as? Long ?: return
        val roomId = sessionAttributes[RoomJoinChannelInterceptor.ROOM_ID_KEY] as? Long ?: return

        reconnectGracePeriodManager.scheduleLeave(roomId, playerId)
    }
}
