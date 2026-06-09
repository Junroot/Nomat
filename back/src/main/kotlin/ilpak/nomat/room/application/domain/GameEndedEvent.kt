package ilpak.nomat.room.application.domain

data class GameEndedEvent(
    val roomId: Long,
    val playerId: Long,
)
