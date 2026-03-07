package ilpak.nomat.room.application.dto

data class RoomJoinedEventMessage(
    val roomId: Long,
    val playerId: Long,
    val nickname: String,
)
