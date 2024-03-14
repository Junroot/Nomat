package ilpak.nomat.room.controller

import ilpak.nomat.room.dto.RoomRequest
import ilpak.nomat.room.dto.RoomResponse
import ilpak.nomat.room.service.RoomService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RequestMapping("/rooms")
@RestController
class RoomController(
	private val roomService: RoomService
) {

	@GetMapping
	fun getRooms(): List<RoomResponse> {
		return roomService.getRooms()
	}

	@GetMapping("/{roomId}")
	fun getRoom(@PathVariable roomId: Long): RoomResponse {
		return roomService.getRoom(roomId)
	}

	@ResponseStatus(HttpStatus.CREATED)
	@PostMapping
	fun createRoom(@RequestBody roomRequest: RoomRequest): RoomResponse {
		return roomService.createRoom(roomRequest)
	}
}
