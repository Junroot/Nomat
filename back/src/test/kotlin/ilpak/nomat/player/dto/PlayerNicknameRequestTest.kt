package ilpak.nomat.player.dto

import ilpak.nomat.infrastructure.validator.HibernateValidator
import ilpak.nomat.player.application.domain.Player
import ilpak.nomat.player.application.dto.PlayerNicknameRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PlayerNicknameRequestTest {

    @ParameterizedTest
    @ValueSource(strings = ["a", "1234567890123456789012345678901234567890"])
    fun name(value: String) {
        val result = HibernateValidator.default.validate(
            PlayerNicknameRequest(
                nickname = value,
            )
        )

        assertThat(result).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "12345678901234567890123456789012345678901"])
    fun invalidName(value: String) {
        val result = HibernateValidator.default.validate(
            PlayerNicknameRequest(
                nickname = value,
            )
        )

        assertThat(result).hasSize(1)
        assertThat(result.first().message).isEqualTo("닉네임은 1자 이상 ${Player.MAX_NICKNAME_LENGTH}자 이하이어야 합니다.")
    }
}
