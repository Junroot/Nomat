package ilpak.nomat.room.dto

data class RoomRequest(
	val title: String,
	val roomCapacity: Int,
	val password: String?,
	val playlistId: Long,
)
