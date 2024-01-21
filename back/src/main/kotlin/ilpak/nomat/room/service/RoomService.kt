package ilpak.nomat.room.service

import ilpak.nomat.room.dto.PlaylistResponse
import ilpak.nomat.room.dto.RoomResponse
import org.springframework.stereotype.Service

@Service
class RoomService {

	fun getRooms(): List<RoomResponse> {
		return (1..40).map { RoomResponse(
			id = it.toLong(),
			title = "들어오셈",
			playlist = PlaylistResponse(
				1L,
				"오늘의 TOP 100: 일본",
				100
			),
			master = "ROOT#3465"
		) }
	}
}
