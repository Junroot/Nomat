package ilpak.nomat.room.application.dto

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = RoomJoinedEventMessage::class, name = "JOINED"),
    JsonSubTypes.Type(value = RoomLeftEventMessage::class, name = "LEFT"),
)
interface RoomEventMessage {
    val roomId: Long
    val playerId: Long
    val nickname: String
}
