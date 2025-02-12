package ilpak.nomat.room.application.domain

import ilpak.nomat.player.application.domain.Player

data class RoomMember(
    val playerId: Long,
    val nickname: String,
)
