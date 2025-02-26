package ilpak.nomat.room.application.dto

data class RoomRequest(
    val title: String,
    val roomCapacity: Int,
    val password: String?,
    val playlistId: Long,
)
