package ilpak.nomat.room.application.dto

import java.time.Instant

data class RoomChatEventMessage(
    override val roomId: Long,
    override val playerId: Long,
    override val nickname: String,
    val content: String,
    val timestamp: Instant,
) : RoomEventMessage
