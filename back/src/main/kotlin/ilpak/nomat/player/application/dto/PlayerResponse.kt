package ilpak.nomat.player.application.dto

import ilpak.nomat.player.application.domain.Player
import ilpak.nomat.player.application.domain.RegistrationType

data class PlayerResponse(
    val nickname: String,
    val displayName: String,
    val registrationType: RegistrationType,
    val registrationId: String,
    val id: Long,
) {
    constructor(player: Player) : this(
        player.nickname,
        player.displayName,
        player.registrationType,
        player.registrationId,
        player.id
    )
}
