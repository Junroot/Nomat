package ilpak.nomat.player.application.dto

import ilpak.nomat.player.application.domain.Player
import ilpak.nomat.player.application.domain.RegistrationType

data class PlayerRequest(
    val nickname: String,
    val registrationType: RegistrationType,
    val registrationId: String,
) {
    fun toDomain(): Player {
        return Player(
            nickname = nickname,
            registrationType = registrationType,
            registrationId = registrationId
        )
    }
}
