package ilpak.nomat.room.application.dto

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = RoomJoinedEventMessage::class, name = "JOINED"),
    JsonSubTypes.Type(value = RoomLeftEventMessage::class, name = "LEFT"),
    JsonSubTypes.Type(value = RoomChatEventMessage::class, name = "CHAT"),
    JsonSubTypes.Type(value = SessionReplacedEventMessage::class, name = "SESSION_REPLACED"),
    JsonSubTypes.Type(value = GameStartedEventMessage::class, name = "STARTED"),
    JsonSubTypes.Type(value = GameEndedEventMessage::class, name = "ENDED"),
    JsonSubTypes.Type(value = RoundStartedEventMessage::class, name = "ROUND_STARTED"),
    JsonSubTypes.Type(value = RoundRevealedEventMessage::class, name = "ROUND_REVEALED"),
)
interface RoomEventMessage {
    val roomId: Long

    // 행위자가 있는 메시지(JOINED/LEFT/CHAT/STARTED…)는 non-null로 override한다.
    // 라운드 이벤트·서버 주도 ENDED처럼 행위자가 없는 메시지는 null을 싣는다.
    val playerId: Long?
    val nickname: String?

    companion object {
        fun channelFor(roomId: Long): String = "room:$roomId:events"
        const val CHANNEL_PATTERN = "room:*:events"
    }
}
