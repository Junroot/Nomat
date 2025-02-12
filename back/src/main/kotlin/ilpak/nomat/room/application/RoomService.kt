package ilpak.nomat.room.application

import ilpak.nomat.infrastructure.exception.NotFoundException
import ilpak.nomat.player.out.jpa.PlayerJpaRepository
import ilpak.nomat.playlist.application.PlaylistService
import ilpak.nomat.room.application.domain.Room
import ilpak.nomat.room.application.domain.RoomMember
import ilpak.nomat.room.application.domain.RoomPlaylist
import ilpak.nomat.room.application.domain.RoomRepository
import ilpak.nomat.room.application.dto.RoomDetailResponse
import ilpak.nomat.room.application.dto.RoomRequest
import ilpak.nomat.room.application.dto.RoomResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
@Service
class RoomService(
    private val playlistService: PlaylistService,
    private val roomRepository: RoomRepository,
    private val playerJpaRepository: PlayerJpaRepository
) {

    fun getRooms(): List<RoomResponse> {
        val rooms = roomRepository.findAll()

        return rooms.map { RoomResponse.of(it) }
    }

    fun getRoomDetail(roomId: Long): RoomDetailResponse {
        val room = roomRepository.findById(roomId) ?: throw NotFoundException("not found room.($roomId)")
        return RoomDetailResponse.of(room)
    }

    @Transactional
    fun createRoom(roomRequest: RoomRequest): RoomDetailResponse {
        val playlistMetadata = playlistService.getMetadata(roomRequest.playlistId)

        val room = Room(
            roomRequest.title,
            roomRequest.password,
            playerJpaRepository.findAll().map { RoomMember(it.id, it.nickname) },
            RoomPlaylist(
                playlistMetadata.name,
                playlistMetadata.count,
                playlistMetadata.master,
                playlistMetadata.comment,
                playlistMetadata.id,
            )
        )
        val savedRoom = roomRepository.save(room)

        return RoomDetailResponse.of(savedRoom)
    }
}
