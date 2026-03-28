package ilpak.nomat.room.application

import ilpak.nomat.common.exception.BadRequestException
import ilpak.nomat.common.exception.ForbiddenException
import ilpak.nomat.common.exception.NotFoundException
import ilpak.nomat.common.exception.NotFoundResource
import ilpak.nomat.common.lock.DistributedLockExecutor
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
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

@Service
@Transactional(readOnly = true)
class RoomService(
    private val playlistService: PlaylistService,
    private val roomRepository: RoomRepository,
    private val roomPlaylistTrackRepository: RoomPlaylistTrackRepository,
    private val playerService: PlayerService,
    private val distributedLockExecutor: DistributedLockExecutor,
    transactionManager: PlatformTransactionManager,
) {

    private val writeTransactionTemplate = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    fun get(cursorRoomId: Long, size: Int): List<RoomResponse> {
        val rooms = roomRepository.findByIdGreaterThanAndStatusOrderByIdDesc(cursorRoomId, RoomStatus.ACTIVE, size)
        val roomIds = rooms.map { it.id }
        val masterIds = rooms.mapNotNull { it.master?.playerId }.toSet()
        val masterIdToDisplayNameMap = playerService.findByIdIn(masterIds).associate { it.id to it.displayName }
        val trackCountsByRoomIdMap = roomPlaylistTrackRepository.countByRoomIds(roomIds)
        val representativeTrackByRoomIdMap = roomPlaylistTrackRepository.findRepresentativeEmbedIdByRoomIds(roomIds)

        return rooms.mapNotNull {
            RoomResponse.of(
                it,
                trackCountsByRoomIdMap[it.id]?.toInt() ?: 0,
                masterIdToDisplayNameMap[it.master?.playerId] ?: return@mapNotNull null,
                representativeTrackByRoomIdMap[it.id],
            )
        }
    }

    fun getDetail(roomId: Long, playerId: Long): RoomDetailResponse {
        val room = roomRepository.findById(roomId) ?: throw NotFoundException(NotFoundResource.ROOM)
        if (!room.playerIds.contains(playerId)) {
            throw ForbiddenException("방 멤버만 조회할 수 있습니다.")
        }
        val players = playerService.findByIdIn(room.playerIds + room.playlistMasterId)
        val playerIdToNicknameMap = players.associate { it.id to it.nickname }
        val trackCount = roomPlaylistTrackRepository.countByRoomId(room).toInt()

        return RoomDetailResponse.of(room, trackCount, playerIdToNicknameMap)
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

        return RoomDetailResponse.of(savedRoom, savedTracks.size, mapOf(playlist.master.id to playlist.master.nickname))
    }

    fun join(roomId: Long, playerId: Long, password: String?) {
        distributedLockExecutor.withLock("room:$roomId:lock") {
            writeTransactionTemplate.executeWithoutResult {
                val room = roomRepository.findById(roomId) ?: throw NotFoundException(NotFoundResource.ROOM)
                room.verifyPassword(password)
                room.join(playerId)
                roomRepository.save(room)
            }
        }
    }

    fun leave(roomId: Long, playerId: Long) {
        distributedLockExecutor.withLock("room:$roomId:lock") {
            writeTransactionTemplate.executeWithoutResult {
                val room = roomRepository.findById(roomId) ?: return@executeWithoutResult
                room.leave(playerId)
                if (room.isEmpty) {
                    roomRepository.delete(room)
                } else {
                    roomRepository.save(room)
                }
            }
        }
    }
}
