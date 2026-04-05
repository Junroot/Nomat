package ilpak.nomat.room.application.dto

import ilpak.nomat.infrastructure.validator.HibernateValidator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class RoomChatRequestTest {

    @Test
    fun validContent() {
        val request = RoomChatRequest(content = "안녕하세요")

        val result = HibernateValidator.default.validate(request)

        assertThat(result).isEmpty()
    }

    @Test
    fun validMaxLengthContent() {
        val request = RoomChatRequest(content = "a".repeat(RoomChatRequest.MAX_CONTENT_LENGTH))

        val result = HibernateValidator.default.validate(request)

        assertThat(result).isEmpty()
    }

    @ParameterizedTest
    @MethodSource("invalidContent")
    fun invalidContent(content: String) {
        val request = RoomChatRequest(content = content)

        val result = HibernateValidator.default.validate(request)

        assertThat(result).hasSize(1)
    }

    companion object {
        @JvmStatic
        fun invalidContent(): List<String> = listOf(
            "",
            " ",
            "a".repeat(RoomChatRequest.MAX_CONTENT_LENGTH + 1),
        )
    }
}
