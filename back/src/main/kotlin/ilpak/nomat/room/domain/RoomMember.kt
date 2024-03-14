package ilpak.nomat.room.domain

import ilpak.nomat.player.domain.Player
import jakarta.persistence.Embeddable

@Embeddable
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
