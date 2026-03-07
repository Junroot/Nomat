package ilpak.nomat.room.application.domain

data class RoomJoinedEvent(
    val roomId: Long,
    val playerId: Long,
)
