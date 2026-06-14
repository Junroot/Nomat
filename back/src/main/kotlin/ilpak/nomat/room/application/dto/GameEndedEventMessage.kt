package ilpak.nomat.room.application.dto

data class GameEndedEventMessage(
    override val roomId: Long,
    override val playerId: Long,
    override val nickname: String,
) : RoomEventMessage
