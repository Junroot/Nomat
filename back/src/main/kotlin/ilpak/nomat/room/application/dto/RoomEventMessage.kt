package ilpak.nomat.room.application.dto

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = RoomJoinedEventMessage::class, name = "JOINED"),
    JsonSubTypes.Type(value = RoomLeftEventMessage::class, name = "LEFT"),
    JsonSubTypes.Type(value = RoomChatEventMessage::class, name = "CHAT"),
    JsonSubTypes.Type(value = SessionReplacedEventMessage::class, name = "SESSION_REPLACED"),
)
interface RoomEventMessage {
    val roomId: Long
    val playerId: Long
    val nickname: String

    companion object {
        fun channelFor(roomId: Long): String = "room:$roomId:events"
        const val CHANNEL_PATTERN = "room:*:events"
    }
}
