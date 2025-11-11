package ilpak.nomat.room.`in`

import ilpak.nomat.room.application.RoomService
import ilpak.nomat.room.application.dto.RoomDetailResponse
import ilpak.nomat.room.application.dto.RoomRequest
import ilpak.nomat.room.application.dto.RoomResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/rooms")
@RestController
private class RoomController(
    private val roomService: RoomService
) {

    @GetMapping
    fun getRooms(): List<RoomResponse> {
        return roomService.getRooms()
    }

    @GetMapping("/{roomId}")
    fun getRoom(@PathVariable roomId: Long): RoomDetailResponse {
        return roomService.getRoomDetail(roomId)
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    fun createRoom(@AuthenticationPrincipal playerId: Long, @RequestBody roomRequest: RoomRequest): RoomDetailResponse {
        return roomService.createRoom(playerId, roomRequest)
    }
}
