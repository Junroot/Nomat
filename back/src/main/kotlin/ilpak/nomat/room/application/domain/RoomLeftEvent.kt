package ilpak.nomat.room.application.domain

data class RoomLeftEvent(
    val roomId: Long,
    val playerId: Long,
)
