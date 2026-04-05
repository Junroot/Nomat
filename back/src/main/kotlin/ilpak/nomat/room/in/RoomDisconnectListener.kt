package ilpak.nomat.room.`in`

import ilpak.nomat.infrastructure.redis.ActiveSessionManager
import ilpak.nomat.infrastructure.web.JwtHandshakeInterceptor
import ilpak.nomat.infrastructure.web.ReconnectGracePeriodManager
import ilpak.nomat.infrastructure.web.RoomJoinChannelInterceptor
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.web.socket.messaging.SessionDisconnectEvent

@Component
private class RoomDisconnectListener(
    private val reconnectGracePeriodManager: ReconnectGracePeriodManager,
    private val activeSessionManager: ActiveSessionManager,
) {

    @EventListener
    fun handleSessionDisconnect(event: SessionDisconnectEvent) {
        val sessionAttributes = event.message.headers["simpSessionAttributes"] as? Map<*, *> ?: return
        val playerId = sessionAttributes[JwtHandshakeInterceptor.PLAYER_ID_KEY] as? Long ?: return
        val roomId = sessionAttributes[RoomJoinChannelInterceptor.ROOM_ID_KEY] as? Long ?: return
        val sessionId = sessionAttributes[RoomJoinChannelInterceptor.SESSION_ID_KEY] as? String ?: return

        if (!activeSessionManager.removeSession(playerId, sessionId)) {
            return
        }

        reconnectGracePeriodManager.scheduleLeave(roomId, playerId)
    }
}
