package ilpak.nomat.room.`in`

import ilpak.nomat.room.application.dto.RoomChatRequest
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller

@Controller
private class RoomChatController {

    @MessageMapping("rooms.{roomId}")
    fun commandRoom(
        @Payload request: RoomChatRequest,
        @DestinationVariable roomId: Long,
        @AuthenticationPrincipal playerId: Long,
    ) {
        /**
         * TODO: Implement room chat logic
         * 1. 방 조회
         * 2. PENDING 상태이면 방장만 입장가능
         * 3. ACTIVE 상태이면 정원 초과 여부, 비밀 번호 확인 후 입장 가능
         */
    }
}
