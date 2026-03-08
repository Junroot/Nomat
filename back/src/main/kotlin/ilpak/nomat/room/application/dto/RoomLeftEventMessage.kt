package ilpak.nomat.room.application.dto

data class RoomLeftEventMessage(
    override val roomId: Long,
    override val playerId: Long,
    override val nickname: String,
) : RoomEventMessage
