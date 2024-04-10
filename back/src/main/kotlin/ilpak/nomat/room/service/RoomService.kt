package ilpak.nomat.room.service

import ilpak.nomat.exception.NotFoundException
import ilpak.nomat.player.repository.PlayerRepository
import ilpak.nomat.playlist.service.PlaylistService
import ilpak.nomat.room.domain.Room
import ilpak.nomat.room.domain.RoomMember
import ilpak.nomat.room.domain.RoomPlaylist
import ilpak.nomat.room.domain.RoomRepository
import ilpak.nomat.room.dto.RoomDetailResponse
import ilpak.nomat.room.dto.RoomRequest
import ilpak.nomat.room.dto.RoomResponse
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
@Service
class RoomService(
    private val playlistService: PlaylistService,
    private val roomRepository: RoomRepository,
    private val playerRepository: PlayerRepository
) {

    fun getRooms(): List<RoomResponse> {
        val rooms = roomRepository.findAll()

        return rooms.mapNotNull { RoomResponse.of(it) }
    }

    fun getRoomDetail(roomId: Long): RoomDetailResponse {
        val room = roomRepository.findByIdOrNull(roomId) ?: throw NotFoundException("not found room.($roomId)")
        return RoomDetailResponse.of(room)
    }

    @Transactional
    fun createRoom(roomRequest: RoomRequest): RoomResponse {
        val playlistMetadata = playlistService.getMetadata(roomRequest.playlistId)

        val room = Room(
            roomRequest.title,
            roomRequest.password,
            playerRepository.findAll().map { RoomMember(it.id, it.nickname) },
            RoomPlaylist(
                playlistMetadata.id,
                playlistMetadata.name,
                playlistMetadata.count,
                playlistMetadata.master,
                playlistMetadata.comment
            )
        )
        val savedRoom = roomRepository.save(room)

        return RoomResponse.of(savedRoom)
    }
}
