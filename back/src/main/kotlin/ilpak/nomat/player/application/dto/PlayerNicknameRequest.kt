package ilpak.nomat.player.application.dto

import ilpak.nomat.player.application.domain.Player
import org.hibernate.validator.constraints.Length

data class PlayerNicknameRequest(
    @field:Length(
        min = 1, max = Player.MAX_NICKNAME_LENGTH,
        message = "닉네임은 {min}자 이상 {max}자 이하이어야 합니다."
    )
    val nickname: String,
)
