package ilpak.nomat.room.dto

import ilpak.nomat.playlist.dto.PlaylistResponse
import ilpak.nomat.room.domain.Room

data class RoomResponse(
	val id: Long,
	val title: String,
	val playlist: PlaylistResponse,
	val masterNickname: String,
) {

	companion object {

		fun of(room: Room, playlist: PlaylistResponse, masterNickname: String): RoomResponse? {
			return RoomResponse(
				room.id ?: return null,
				room.title,
				playlist,
				masterNickname
			)
		}
	}
}
