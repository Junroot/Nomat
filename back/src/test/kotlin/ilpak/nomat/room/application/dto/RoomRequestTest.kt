package ilpak.nomat.room.application.dto

import ilpak.nomat.infrastructure.validator.HibernateValidator
import ilpak.nomat.room.application.domain.Room
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class RoomRequestTest {

    @Test
    fun validRoomRequestWithPassword() {
        val request = RoomRequest(
            title = "방 제목",
            password = "password123",
            maxEntriesCount = 10,
            playlistId = 1L
        )

        val result = HibernateValidator.default.validate(request)

        assertThat(result).isEmpty()
    }

    @Test
    fun validRoomRequestWithoutPassword() {
        val request = RoomRequest(
            title = "방 제목",
            password = null,
            maxEntriesCount = 5,
            playlistId = 1L
        )

        val result = HibernateValidator.default.validate(request)

        assertThat(result).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = ["a", "123456789012345678901234567890"])
    fun validTitle(value: String) {
        val request = RoomRequest(
            title = value,
            password = "password",
            maxEntriesCount = 10,
            playlistId = 1L
        )

        val result = HibernateValidator.default.validate(request)

        assertThat(result).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "1234567890123456789012345678901"])
    fun invalidTitle(title: String) {
        val request = RoomRequest(
            title = title,
            password = "password",
            maxEntriesCount = 10,
            playlistId = 1L
        )

        val result = HibernateValidator.default.validate(request)

        assertThat(result).hasSize(1)
        assertThat(result.first().message).isEqualTo("방 제목은 1자 이상 ${Room.MAX_TITLE_LENGTH}자 이하이어야 합니다.")
    }

    @ParameterizedTest
    @ValueSource(strings = ["a", "123456789012345678901234567890"])
    fun validPassword(value: String) {
        val request = RoomRequest(
            title = "방 제목",
            password = value,
            maxEntriesCount = 10,
            playlistId = 1L
        )

        val result = HibernateValidator.default.validate(request)

        assertThat(result).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "1234567890123456789012345678901"])
    fun invalidPassword(password: String) {
        val request = RoomRequest(
            title = "방 제목",
            password = password,
            maxEntriesCount = 10,
            playlistId = 1L
        )

        val result = HibernateValidator.default.validate(request)

        assertThat(result).hasSize(1)
        assertThat(result.first().message).isEqualTo("비밀번호는 1자 이상 ${Room.MAX_PASSWORD_LENGTH}자 이하이어야 합니다.")
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 10, 20])
    fun validMaxEntriesCount(value: Int) {
        val request = RoomRequest(
            title = "방 제목",
            password = "password",
            maxEntriesCount = value,
            playlistId = 1L
        )

        val result = HibernateValidator.default.validate(request)

        assertThat(result).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(ints = [0, -1, 21, 100])
    fun invalidMaxEntriesCount(value: Int) {
        val request = RoomRequest(
            title = "방 제목",
            password = "password",
            maxEntriesCount = value,
            playlistId = 1L
        )

        val result = HibernateValidator.default.validate(request)

        assertThat(result).hasSize(1)
        assertThat(result.first().message).isEqualTo("최대 인원수는 1명 이상 ${Room.MAX_MAX_ENTRIES_COUNT}명 이하이어야 합니다.")
    }
}

