package ilpak.nomat.room.application.dto

data class SessionReplacedEventMessage(
    override val roomId: Long,
    override val playerId: Long,
    override val nickname: String,
) : RoomEventMessage
