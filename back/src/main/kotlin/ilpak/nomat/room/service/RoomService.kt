package ilpak.nomat.room.service

import ilpak.nomat.playlist.service.PlaylistService
import ilpak.nomat.room.domain.RoomRepository
import ilpak.nomat.room.dto.RoomResponse
import ilpak.nomat.user.service.UserService
import org.springframework.stereotype.Service

@Service
class RoomService(
	private val playlistService: PlaylistService,
	private val userService: UserService,
	private val roomRepository: RoomRepository
) {

	fun getRooms(): List<RoomResponse> {
		val rooms = roomRepository.findAll()
		val playlists = playlistService.getPlaylistMetadata(rooms.map { it.playlistId }.toSet())
			.associateBy { it.id }
		val masterNicknames = userService.getUserNames(rooms.map { it.masterId }.toSet())
			.associateBy { it.userId }

		return rooms.mapNotNull {
			RoomResponse.of(
				it,
				playlists[it.playlistId] ?: return@mapNotNull null,
				masterNicknames[it.masterId]?.nickname ?: return@mapNotNull null
			)
		}
	}
}
