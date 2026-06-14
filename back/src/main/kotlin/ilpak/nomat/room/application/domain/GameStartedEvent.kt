package ilpak.nomat.room.application.domain

data class GameStartedEvent(
    val roomId: Long,
    val playerId: Long,
)
