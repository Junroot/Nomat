package ilpak.nomat.room.application

import ilpak.nomat.common.exception.BadRequestException
import ilpak.nomat.common.exception.NotFoundException
import ilpak.nomat.common.exception.NotFoundResource
import ilpak.nomat.player.application.PlayerService
import ilpak.nomat.playlist.application.PlaylistService
import ilpak.nomat.room.application.domain.Room
import ilpak.nomat.room.application.domain.RoomPlaylist
import ilpak.nomat.room.application.domain.RoomPlaylistTrack
import ilpak.nomat.room.application.domain.RoomPlaylistTrackRepository
import ilpak.nomat.room.application.domain.RoomRepository
import ilpak.nomat.room.application.domain.RoomStatus
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
    private val roomPlaylistTrackRepository: RoomPlaylistTrackRepository,
    private val playerService: PlayerService,
) {

    fun get(cursorRoomId: Long, size: Int): List<RoomResponse> {
        val rooms = roomRepository.findByIdLessThanAndStatusOrderByIdDesc(cursorRoomId, RoomStatus.ACTIVE, size)
        val masterIds = rooms.mapNotNull { it.master?.playerId }.toSet()
        val masterIdToNicknameMap = playerService.findByIdIn(masterIds).associate { it.id to it.nickname }
        val trackCountsByRoomIdMap = roomPlaylistTrackRepository.countByRoomIds(rooms.map { it.id })

        return rooms.mapNotNull {
            RoomResponse.of(
                it,
                trackCountsByRoomIdMap[it.id]?.toInt() ?: 0,
                masterIdToNicknameMap[it.playlistMasterId] ?: return@mapNotNull null
            )
        }
    }

    fun getDetail(roomId: Long): RoomDetailResponse {
        val room = roomRepository.findById(roomId) ?: throw NotFoundException(NotFoundResource.ROOM)
        val players = playerService.findByIdIn(room.playerIds + room.playlistMasterId)
        val playerIdToNicknameMap = players.associate { it.id to it.nickname }
        val trackCount = roomPlaylistTrackRepository.countByRoomId(room).toInt()

        return RoomDetailResponse.of(room, trackCount, playerIdToNicknameMap)
    }

    @Transactional
    fun save(requestPlayerId: Long, roomRequest: RoomRequest): RoomDetailResponse {
        val playlist = try {
            playlistService.getWithTracksForInternal(roomRequest.playlistId)
        } catch (notfoundException: NotFoundException) {
            if (notfoundException.resource == NotFoundResource.PLAYLIST) {
                throw BadRequestException("존재하지 않는 플레이리스트입니다.")
            }
            throw notfoundException
        }

        val room = Room(
            roomRequest.title,
            roomRequest.password,
            roomRequest.maxEntriesCount,
            RoomPlaylist(
                playlist.title,
                playlist.master.id,
                playlist.description,
                playlist.id,
            ),
        )
        val savedRoom = roomRepository.save(room)
        val roomPlaylistTracks = playlist.tracks.map {
            RoomPlaylistTrack(
                it.embedId,
                it.title,
                it.startTimeSec,
                it.endTimeSec,
                it.repeatCount,
                it.additionalTitles,
                it.isRepresentative,
                room,
                it.id
            )
        }
        val savedTracks = roomPlaylistTrackRepository.save(roomPlaylistTracks)

        return RoomDetailResponse.of(savedRoom, savedTracks.size, mapOf(playlist.master.id to playlist.master.nickname))
    }
}
