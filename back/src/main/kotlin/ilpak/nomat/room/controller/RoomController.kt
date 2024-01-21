package ilpak.nomat.room.controller

import ilpak.nomat.room.dto.RoomResponse
import ilpak.nomat.room.service.RoomService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/rooms")
@RestController
class RoomController(
	private val roomService: RoomService
) {

	@GetMapping
	fun getRooms(): List<RoomResponse> {
		return roomService.getRooms()
	}
}
