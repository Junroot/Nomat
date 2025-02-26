package ilpak.nomat.room.application.dto

data class RoomMemberResponse(
    val nickname: String,
    val photoUrl: String,
    val isMaster: Boolean
)
