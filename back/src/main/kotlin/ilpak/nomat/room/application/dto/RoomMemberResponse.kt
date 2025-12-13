package ilpak.nomat.room.application.dto

data class RoomMemberResponse(
    val nickname: String,
    val isMaster: Boolean,
    val id: Long,
)
