package ilpak.nomat.room.`in`

import ilpak.nomat.room.application.RoomService
import ilpak.nomat.room.application.dto.RoomJoinRequest
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller

@Controller
private class RoomChatController(
    private val roomService: RoomService
) {

    @MessageMapping("rooms.{roomId}.join")
    @SendTo("/topic/rooms.{roomId}.joined")
    fun joinRoom(
        @Payload request: RoomJoinRequest,
        @DestinationVariable roomId: Long,
        @AuthenticationPrincipal playerId: Long,
    ): Map<String, Any> {
        //TODO
        // subscribe으로 방 입장 구현 하도록 수정
        // 방 입장 성공했을 때 해당 방에 broadcast
        roomService.join(playerId, roomId, request)
        return mapOf(
            "playerId" to playerId,
            "success" to true
        )
    }
}
