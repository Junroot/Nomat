package ilpak.nomat.room.application.domain

import ilpak.nomat.player.application.domain.Player

data class RoomMember(
    val playerId: Long,
    val nickname: String,
) {
    companion object {
        fun of(player: Player): RoomMember {
            return RoomMember(player.id, player.nickname)
        }
    }
}
