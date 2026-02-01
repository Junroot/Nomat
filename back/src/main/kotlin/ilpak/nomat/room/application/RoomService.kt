package ilpak.nomat.room.application

import ilpak.nomat.common.exception.BadRequestException
import ilpak.nomat.common.exception.NotFoundException
import ilpak.nomat.common.exception.NotFoundResource
import ilpak.nomat.player.application.PlayerService
import ilpak.nomat.playlist.application.PlaylistService
import ilpak.nomat.room.application.domain.Room
import ilpak.nomat.room.application.domain.RoomEntries
import ilpak.nomat.room.application.domain.RoomEntryRepository
import ilpak.nomat.room.application.domain.RoomEntryResult
import ilpak.nomat.room.application.domain.RoomPlaylist
import ilpak.nomat.room.application.domain.RoomPlaylistTrack
import ilpak.nomat.room.application.domain.RoomPlaylistTrackRepository
import ilpak.nomat.room.application.domain.RoomRepository
import ilpak.nomat.room.application.domain.RoomStatus
import ilpak.nomat.room.application.dto.RoomDetailResponse
import ilpak.nomat.room.application.dto.RoomJoinRequest
import ilpak.nomat.room.application.dto.RoomRequest
import ilpak.nomat.room.application.dto.RoomResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class RoomService(
    private val playlistService: PlaylistService,
    private val roomRepository: RoomRepository,
    private val roomEntryRepository: RoomEntryRepository,
    private val roomPlaylistTrackRepository: RoomPlaylistTrackRepository,
    private val playerService: PlayerService,
) {

    fun get(cursorRoomId: Long, size: Int): List<RoomResponse> {
        val rooms = roomRepository.findByIdLessThanAndStatusOrderByIdDesc(cursorRoomId, RoomStatus.ACTIVE, size)
        val roomIdToEntries = roomEntryRepository.getEntries(rooms.map { it.id })
        val masterIds = roomIdToEntries.mapNotNull { it.value.masterEntry?.playerId }.toSet()
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
        val entries = roomEntryRepository.getEntries(roomId)
        val players = playerService.findByIdIn(entries.playerIds.toSet() + room.playlistMasterId)
        val playerIdToNicknameMap = players.associate { it.id to it.nickname }
        val trackCount = roomPlaylistTrackRepository.countByRoomId(room).toInt()

        return RoomDetailResponse.of(room, trackCount, entries, playerIdToNicknameMap)
    }

    @Transactional
    fun save(roomRequest: RoomRequest): RoomDetailResponse {
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

        return RoomDetailResponse.of(savedRoom,
            savedTracks.size,
            RoomEntries(),
            mapOf(playlist.master.id to playlist.master.nickname)
        )
    }

    @Transactional
    fun join(playerId: Long, roomId: Long, request: RoomJoinRequest) {
        val room = roomRepository.findById(roomId) ?: throw NotFoundException(NotFoundResource.ROOM)

        if (room.password != request.password) {
            throw BadRequestException("비밀번호가 일치하지 않습니다.")
        }

        val entryResult = roomEntryRepository.tryEnter(roomId, playerId, room.maxEntriesCount)

        when (entryResult) {
            RoomEntryResult.SUCCESS -> {}
            RoomEntryResult.ALREADY_JOINED -> throw BadRequestException("이미 방에 참여 중입니다.")
            RoomEntryResult.ROOM_FULL -> throw BadRequestException("방이 가득 찼습니다.")
        }

        if (room.status == RoomStatus.PENDING) {
            room.status = RoomStatus.ACTIVE
        }
    }
}
