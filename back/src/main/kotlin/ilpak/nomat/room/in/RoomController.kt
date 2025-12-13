package ilpak.nomat.room.`in`

import ilpak.nomat.room.application.RoomService
import ilpak.nomat.room.application.dto.RoomDetailResponse
import ilpak.nomat.room.application.dto.RoomRequest
import ilpak.nomat.room.application.dto.RoomResponse
import jakarta.validation.constraints.Max
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/rooms")
@RestController
@Validated
private class RoomController(
    private val roomService: RoomService
) {

    @GetMapping
    fun get(
        @RequestParam(defaultValue = "0") cursorRoomId: Long,
        @RequestParam(defaultValue = "100") @Max(100) size: Int
    ): List<RoomResponse> {
        return roomService.get(cursorRoomId, size)
    }

    @GetMapping("/{roomId}")
    fun getDetail(@PathVariable roomId: Long): RoomDetailResponse {
        return roomService.getDetail(roomId)
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    fun save(@AuthenticationPrincipal playerId: Long, @RequestBody roomRequest: RoomRequest): RoomDetailResponse {
        return roomService.save(playerId, roomRequest)
    }
}
