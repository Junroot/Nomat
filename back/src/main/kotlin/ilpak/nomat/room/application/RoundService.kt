package ilpak.nomat.room.application

import ilpak.nomat.common.lock.DistributedLockExecutor
import ilpak.nomat.room.application.domain.AnswerMatcher
import ilpak.nomat.room.application.domain.RoomPlaylistTrack
import ilpak.nomat.room.application.domain.RoomPlaylistTrackRepository
import ilpak.nomat.room.application.domain.RoomRepository
import ilpak.nomat.room.application.domain.RoundPhase
import ilpak.nomat.room.application.domain.RoundRevealedEvent
import ilpak.nomat.room.application.domain.RoundStartedEvent
import ilpak.nomat.room.application.domain.RoundStateStore
import ilpak.nomat.room.application.domain.RoundTrackRef
import ilpak.nomat.room.application.domain.RoundTrackSpec
import ilpak.nomat.room.application.domain.RoundTransition
import ilpak.nomat.room.application.domain.TransitionResult
import ilpak.nomat.room.application.dto.RoundSnapshotResponse
import ilpak.nomat.room.application.dto.RoundTrackRefResponse
import ilpak.nomat.room.application.dto.ScoreEntryResponse
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

/**
 * 라운드 진행 오케스트레이션의 단일 진입점. 모든 전이 트리거(게임 시작·첫 정답·sweeper 타임아웃·종료)가
 * 여기로 수렴해 `RoundStateStore`의 원자 CAS를 통과한다. 전이 성공(CAS==TRANSITIONED)일 때만 후속
 * 사이드이펙트(점수 반영은 CAS 내부, 라운드 이벤트 발행, 다음 트랙 선정, 종료 시 방 상태 플립)를 수행한다.
 *
 * 라운드 전이 핫패스는 분산 락을 쓰지 않는다(멱등성은 락이 아니라 Lua CAS에 있다 — Decision 3).
 * 종료 시 DB `room.status` 플립만 멤버십 락을 재사용한다.
 */
@Service
class RoundService(
    private val roundStateStore: RoundStateStore,
    private val roomRepository: RoomRepository,
    private val roomPlaylistTrackRepository: RoomPlaylistTrackRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val distributedLockExecutor: DistributedLockExecutor,
    transactionManager: PlatformTransactionManager,
) {

    private val writeTransactionTemplate = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    /** 게임 시작 시 라운드 순서를 셔플해 고정하고 첫 라운드를 `OPEN`으로 연다. */
    fun startRound(roomId: Long) {
        val tracks = roomPlaylistTrackRepository.findByRoomId(roomId)
        if (tracks.isEmpty()) {
            return
        }
        val playerIds = roomRepository.findPlayerIdsByRoomId(roomId)
        val shuffled = tracks.shuffled()
        val specs = shuffled.map { RoundTrackSpec(it.trackId, openDurationMillis(it)) }
        val transition = roundStateStore.start(roomId, specs, playerIds)
        if (transition.result == TransitionResult.TRANSITIONED) {
            publishRoundStarted(roomId, transition, shuffled[transition.trackIndex], shuffled.size)
        }
    }

    /**
     * 채팅을 정답으로 판정해 정답이면 `OPEN→REVEAL` CAS를 시도하고, 성공 시에만 `ROUND_REVEALED`를 발행한다.
     * 채팅 원문 방송은 호출자(`RoomStompController`)가 정답 여부와 무관하게 항상 수행하므로 여기서는 전이만 담당한다.
     */
    fun submitAnswer(roomId: Long, playerId: Long, content: String) {
        val snapshot = roundStateStore.snapshot(roomId) ?: return
        if (snapshot.phase != RoundPhase.OPEN) {
            return
        }
        val tracks = roomPlaylistTrackRepository.findByRoomId(roomId)
        val track = trackOf(tracks, snapshot.currentTrackId) ?: return
        if (!AnswerMatcher.matches(content, track.additionalTitles + track.title)) {
            return
        }
        val transition = roundStateStore.tryAdvanceOnCorrect(roomId, snapshot.roundSeq, playerId)
        if (transition.result == TransitionResult.TRANSITIONED) {
            publishRoundRevealed(roomId, transition, track, snapshot.nextTrackId()?.let { trackOf(tracks, it) })
        }
    }

    /** sweeper가 호출하는 마감 라운드 전이 구동기. 마감 지난 모든 방을 단일 CAS로 다음 단계로 옮긴다. */
    fun sweepDueRounds() {
        for (roomId in roundStateStore.findDueRoomIds()) {
            advanceDueRoom(roomId)
        }
    }

    /** 게임 종료·방 삭제 시 라운드 상태 정리. */
    fun teardownRound(roomId: Long) {
        roundStateStore.teardown(roomId)
    }

    /** 게임 중 퇴장 시 점수판에서 제거. */
    fun onPlayerLeft(roomId: Long, playerId: Long) {
        roundStateStore.removeScore(roomId, playerId)
    }

    /** 재접속 복원용 스냅샷. `OPEN` 중에는 정답을 제외하고 `REVEAL`·`ENDED`에서만 포함한다. */
    fun getSnapshot(roomId: Long): RoundSnapshotResponse? {
        val snapshot = roundStateStore.snapshot(roomId) ?: return null
        val tracks = roomPlaylistTrackRepository.findByRoomId(roomId)
        val track = trackOf(tracks, snapshot.currentTrackId) ?: return null
        val revealed = snapshot.phase != RoundPhase.OPEN
        // 다음 트랙은 REVEAL에서만 — 선버퍼링할 구간이 그때뿐이다. `OPEN`에 실으면 다음 라운드
        // 정답이 라운드 내내 노출되고, `ENDED`에는 이어질 라운드가 없다.
        val nextTrack = if (snapshot.phase == RoundPhase.REVEAL) {
            snapshot.nextTrackId()?.let { trackOf(tracks, it) }?.let(RoundTrackRefResponse::of)
        } else {
            null
        }
        return RoundSnapshotResponse(
            phase = snapshot.phase,
            roundSeq = snapshot.roundSeq,
            roundNumber = snapshot.trackIndex + 1,
            totalRounds = snapshot.totalRounds,
            deadlineAt = snapshot.deadlineAt,
            currentTrack = RoundTrackRefResponse.of(track),
            title = if (revealed) track.title else null,
            winnerId = snapshot.winnerId,
            scores = snapshot.scores.map { ScoreEntryResponse(it.playerId, it.score) },
            nextTrack = nextTrack,
        )
    }

    private fun advanceDueRoom(roomId: Long) {
        val snapshot = roundStateStore.snapshot(roomId)
        if (snapshot == null) {
            roundStateStore.teardown(roomId)
            return
        }
        if (roomRepository.findById(roomId) == null) {
            roundStateStore.teardown(roomId)
            return
        }
        val transition = roundStateStore.tryAdvanceOnDeadline(roomId, snapshot.roundSeq)
        if (transition.result != TransitionResult.TRANSITIONED) {
            return
        }
        when (transition.phase) {
            RoundPhase.REVEAL -> {
                val tracks = roomPlaylistTrackRepository.findByRoomId(roomId)
                val track = trackOf(tracks, snapshot.currentTrackId) ?: return
                publishRoundRevealed(roomId, transition, track, snapshot.nextTrackId()?.let { trackOf(tracks, it) })
            }

            RoundPhase.OPEN -> {
                val nextTrackId = snapshot.trackOrder[transition.trackIndex]
                val track = trackOf(roomPlaylistTrackRepository.findByRoomId(roomId), nextTrackId) ?: return
                publishRoundStarted(roomId, transition, track, snapshot.totalRounds)
            }

            RoundPhase.ENDED -> flipRoomToActive(roomId)
            null -> Unit
        }
    }

    private fun flipRoomToActive(roomId: Long) {
        distributedLockExecutor.withLock("room:$roomId:lock") {
            writeTransactionTemplate.executeWithoutResult {
                val room = roomRepository.findById(roomId) ?: return@executeWithoutResult
                room.endByEngine()
                roomRepository.save(room)
            }
        }
    }

    private fun publishRoundStarted(
        roomId: Long,
        transition: RoundTransition,
        track: RoomPlaylistTrack,
        totalRounds: Int,
    ) {
        eventPublisher.publishEvent(
            RoundStartedEvent(
                roomId = roomId,
                roundSeq = transition.roundSeq,
                roundNumber = transition.trackIndex + 1,
                totalRounds = totalRounds,
                deadlineAt = transition.deadlineAt,
                embedId = track.embedId,
                startTimeSec = track.startTimeSec,
                endTimeSec = track.endTimeSec,
                repeatCount = track.repeatCount,
            )
        )
    }

    private fun publishRoundRevealed(
        roomId: Long,
        transition: RoundTransition,
        track: RoomPlaylistTrack,
        nextTrack: RoomPlaylistTrack?,
    ) {
        eventPublisher.publishEvent(
            RoundRevealedEvent(
                roomId = roomId,
                roundSeq = transition.roundSeq,
                winnerId = transition.winnerId,
                title = track.title,
                scores = roundStateStore.scoreboard(roomId),
                nextTrack = nextTrack?.let {
                    RoundTrackRef(it.embedId, it.startTimeSec, it.endTimeSec, it.repeatCount)
                },
            )
        )
    }

    private fun openDurationMillis(track: RoomPlaylistTrack): Long {
        val clipMillis = (track.endTimeSec - track.startTimeSec).toLong() * track.repeatCount * MILLIS_PER_SECOND
        return clipMillis.coerceAtLeast(0) + BUFFER_MILLIS
    }

    private fun trackOf(tracks: List<RoomPlaylistTrack>, trackId: Long): RoomPlaylistTrack? =
        tracks.firstOrNull { it.trackId == trackId }

    companion object {
        private const val MILLIS_PER_SECOND = 1_000L
        private const val BUFFER_MILLIS = 2_000L
    }
}
