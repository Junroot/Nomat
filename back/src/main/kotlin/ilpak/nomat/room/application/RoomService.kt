package ilpak.nomat.room.application

import ilpak.nomat.common.exception.BadRequestException
import ilpak.nomat.common.exception.ForbiddenException
import ilpak.nomat.common.exception.NotFoundException
import ilpak.nomat.common.exception.NotFoundResource
import ilpak.nomat.common.lock.DistributedLockExecutor
import ilpak.nomat.player.application.PlayerService
import ilpak.nomat.playlist.application.PlaylistService
import ilpak.nomat.room.application.domain.PendingLeaveStore
import ilpak.nomat.room.application.domain.Room
import ilpak.nomat.room.application.domain.RoomPlaylist
import ilpak.nomat.room.application.domain.RoomPlaylistTrack
import ilpak.nomat.room.application.domain.RoomPlaylistTrackRepository
import ilpak.nomat.room.application.domain.RoomRepository
import ilpak.nomat.room.application.domain.RoomStatus
import ilpak.nomat.room.application.dto.RoomDetailResponse
import ilpak.nomat.room.application.dto.RoomRequest
import ilpak.nomat.room.application.dto.RoomResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Propagation
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
    private val roundService: RoundService,
    private val pendingLeaveStore: PendingLeaveStore,
    @Value("\${app.room.reconnect-grace-period-seconds:60}") private val gracePeriodSeconds: Long,
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
                representativeTrackByRoomIdMap[it.id] ?: return@mapNotNull null,
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
        val round = roundService.getSnapshot(roomId, playerId)

        return RoomDetailResponse.of(room, trackCount, playerIdToNicknameMap, round)
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

    /**
     * 방 퇴장. 방이 없거나 멤버가 아니면 **조용한 no-op**이다 — 이 성질이 [sweepDueLeaves]의 "예외 없이 반환하면 완료"
     * 판별의 근거이므로, 여기서 예외를 던지도록 바꾸면 sweeper의 완료/재시도 규칙도 함께 바꿔야 한다.
     */
    fun leave(roomId: Long, playerId: Long) {
        var roomDeleted = false
        distributedLockExecutor.withLock("room:$roomId:lock") {
            writeTransactionTemplate.executeWithoutResult {
                val room = roomRepository.findById(roomId) ?: return@executeWithoutResult
                room.leave(playerId)
                if (room.isEmpty) {
                    roomRepository.delete(room)
                    roomDeleted = true
                } else {
                    roomRepository.save(room)
                }
            }
        }
        if (roomDeleted) {
            roundService.teardownRound(roomId)
        }
    }

    /**
     * 연결이 끊긴 멤버의 퇴장을 유예 시간 뒤로 예약한다. 예약은 Redis에 두므로 이 인스턴스가 내려가도 남고,
     * 어느 인스턴스의 재접속이든 [cancelPendingLeave]로 취소할 수 있다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun scheduleLeave(roomId: Long, playerId: Long) {
        pendingLeaveStore.schedule(roomId, playerId, gracePeriodSeconds)
        log.info("퇴장 유예 예약: roomId={}, playerId={}, graceSeconds={}", roomId, playerId, gracePeriodSeconds)
    }

    /** 유예 예약 취소. 예약이 있었으면 `true` — 세션 없는 CONNECT를 재접속으로 볼지 신규 입장으로 볼지 이 값이 가른다. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun cancelPendingLeave(roomId: Long, playerId: Long): Boolean {
        val cancelled = pendingLeaveStore.remove(roomId, playerId)
        if (cancelled) {
            log.info("재접속으로 유예 취소: roomId={}, playerId={}", roomId, playerId)
        }
        return cancelled
    }

    /**
     * 만료된 유예 예약 처리(sweeper 구동). 항목마다 **먼저 `remove`로 claim하고 성공한 경우에만** [leave]를 실행한다
     * (claim-then-act) — 재접속이 먼저 취소했으면 0을 받아 건너뛰므로 접속 중인 멤버를 내보내지 않는다.
     * [leave]가 예외 없이 반환하면 완료(방 없음·이미 퇴장 포함), 예외면 경고 로그 후 재시도 간격 뒤로 복원한다.
     * 한 항목의 실패가 나머지 처리를 막지 않는다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun sweepDueLeaves() {
        for (pending in pendingLeaveStore.findDue()) {
            if (!pendingLeaveStore.remove(pending.roomId, pending.playerId)) {
                continue
            }
            @Suppress("TooGenericExceptionCaught")
            try {
                leave(pending.roomId, pending.playerId)
                log.info("유예 시간 만료로 퇴장 처리: roomId={}, playerId={}", pending.roomId, pending.playerId)
            } catch (exception: Exception) {
                log.warn(
                    "유예 만료 퇴장 실패, 재시도 예약: roomId={}, playerId={}",
                    pending.roomId,
                    pending.playerId,
                    exception,
                )
                pendingLeaveStore.restore(pending.roomId, pending.playerId)
            }
        }
    }

    fun start(roomId: Long, playerId: Long) {
        distributedLockExecutor.withLock("room:$roomId:lock") {
            writeTransactionTemplate.executeWithoutResult {
                val room = roomRepository.findById(roomId) ?: throw NotFoundException(NotFoundResource.ROOM)
                room.start(playerId)
                roomRepository.save(room)
            }
        }
        roundService.startRound(roomId)
    }

    fun end(roomId: Long, playerId: Long) {
        distributedLockExecutor.withLock("room:$roomId:lock") {
            writeTransactionTemplate.executeWithoutResult {
                val room = roomRepository.findById(roomId) ?: throw NotFoundException(NotFoundResource.ROOM)
                room.end(playerId)
                roomRepository.save(room)
            }
        }
        roundService.teardownRound(roomId)
    }

    companion object {
        private val log = LoggerFactory.getLogger(RoomService::class.java)
    }
}
