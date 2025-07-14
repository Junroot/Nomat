package ilpak.nomat.room.application

import ilpak.nomat.infrastructure.exception.NotFoundException
import ilpak.nomat.infrastructure.exception.NotFoundResource
import ilpak.nomat.player.application.PlayerService
import ilpak.nomat.playlist.application.PlaylistService
import ilpak.nomat.room.application.domain.Room
import ilpak.nomat.room.application.domain.RoomEntry
import ilpak.nomat.room.application.domain.RoomPlaylist
import ilpak.nomat.room.application.domain.RoomRepository
import ilpak.nomat.room.application.dto.RoomDetailResponse
import ilpak.nomat.room.application.dto.RoomRequest
import ilpak.nomat.room.application.dto.RoomResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class RoomService(
    private val playlistService: PlaylistService,
    private val roomRepository: RoomRepository,
    private val playerService: PlayerService,
) {

    fun getRooms(): List<RoomResponse> {
        val rooms = roomRepository.findAll()
        val masterIds = rooms.mapNotNull { it.master?.playerId }.toSet()
        val nicknameByMasterId = playerService.findByIdIn(masterIds).associate { it.id to it.nickname }

        return rooms.mapNotNull {
            val masterId = it.master?.playerId ?: return@mapNotNull null
            val nickname = nicknameByMasterId[masterId] ?: return@mapNotNull null
            RoomResponse.of(it, nickname)
        }
    }

    fun getRoomDetail(roomId: Long): RoomDetailResponse {
        val room = roomRepository.findById(roomId) ?: throw NotFoundException(NotFoundResource.ROOM)
        val players = playerService.findByIdIn(room.playerIds + room.playlistMasterId)
        val nicknameByPlayerId = players.associate { it.id to it.nickname }
        return RoomDetailResponse.of(room, nicknameByPlayerId)
    }

    @Transactional
    fun createRoom(roomRequest: RoomRequest): RoomDetailResponse {
        val playlistMetadata = playlistService.getPlaylistMetadata(roomRequest.playlistId)

        val room = Room(
            roomRequest.title,
            roomRequest.password,
            RoomPlaylist(
                playlistMetadata.name,
                playlistMetadata.trackCount,
                playlistMetadata.masterId,
                playlistMetadata.comment,
                playlistMetadata.id,
            )
        )
        val savedRoom = roomRepository.save(room)
        val players = playerService.findAll()
        savedRoom.entries.addAll(players.map { RoomEntry(it.id) })
        val nicknameByPlayerId = players.associate { it.id to it.nickname }

        return RoomDetailResponse.of(savedRoom, nicknameByPlayerId)
    }
}
