package ilpak.nomat.room.dto

data class RoomResponse(
	val id: Long,
	val title: String,
	val playlist: PlaylistResponse,
	val master: String,
)
