package ilpak.nomat.room.application.dto

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SessionReplacedEventMessageTest {

    private val objectMapper = ObjectMapper().registerKotlinModule()

    @Test
    fun `직렬화 시 type 필드가 SESSION_REPLACED로 포함된다`() {
        val message: RoomEventMessage = SessionReplacedEventMessage(
            roomId = 1L,
            playerId = 2L,
            nickname = "testUser",
        )

        val json = objectMapper.writeValueAsString(message)

        assertThat(json).contains("\"type\":\"SESSION_REPLACED\"")
        assertThat(json).contains("\"roomId\":1")
        assertThat(json).contains("\"playerId\":2")
        assertThat(json).contains("\"nickname\":\"testUser\"")
    }

    @Test
    fun `역직렬화 시 SessionReplacedEventMessage로 복원된다`() {
        val json = """{"type":"SESSION_REPLACED","roomId":1,"playerId":2,"nickname":"testUser"}"""

        val message = objectMapper.readValue(json, RoomEventMessage::class.java)

        assertThat(message).isInstanceOf(SessionReplacedEventMessage::class.java)
        val sessionReplaced = message as SessionReplacedEventMessage
        assertThat(sessionReplaced.roomId).isEqualTo(1L)
        assertThat(sessionReplaced.playerId).isEqualTo(2L)
        assertThat(sessionReplaced.nickname).isEqualTo("testUser")
    }
}
