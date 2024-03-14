package ilpak.nomat.room.dto

import ilpak.nomat.room.domain.Room

data class RoomResponse(
	val id: Long,
	val title: String,
	val playlist: PlaylistResponse,
	val masterNickname: String?,
) {

	companion object {

		fun of(room: Room): RoomResponse {
			return RoomResponse(
				room.id,
				room.title,
				PlaylistResponse.of(room.playlist),
				room.master?.nickname,
			)
		}
	}
}
