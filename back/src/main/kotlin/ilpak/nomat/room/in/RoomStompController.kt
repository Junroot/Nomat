package ilpak.nomat.room.`in`

import ilpak.nomat.infrastructure.web.JwtHandshakeInterceptor
import ilpak.nomat.infrastructure.web.RoomJoinChannelInterceptor
import ilpak.nomat.room.application.RoomService
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.stereotype.Controller

@Controller
private class RoomStompController(
    private val roomService: RoomService,
) {

    @MessageMapping("/rooms/leave")
    fun leave(headerAccessor: SimpMessageHeaderAccessor) {
        val playerId = headerAccessor.sessionAttributes?.get(JwtHandshakeInterceptor.PLAYER_ID_KEY) as? Long ?: return
        val roomId = headerAccessor.sessionAttributes?.get(RoomJoinChannelInterceptor.ROOM_ID_KEY) as? Long ?: return
        roomService.leave(roomId, playerId)
    }
}
