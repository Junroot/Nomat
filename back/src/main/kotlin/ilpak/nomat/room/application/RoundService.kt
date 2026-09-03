package ilpak.nomat.room.application

import ilpak.nomat.common.lock.DistributedLockExecutor
import ilpak.nomat.room.application.domain.AnswerMatcher
import ilpak.nomat.room.application.domain.PassOutcome
import ilpak.nomat.room.application.domain.RoomPlaylistTrack
import ilpak.nomat.room.application.domain.RoomPlaylistTrackRepository
import ilpak.nomat.room.application.domain.RoomRepository
import ilpak.nomat.room.application.domain.RoundPassUpdatedEvent
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
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

/**
 * 라운드 진행 오케스트레이션의 단일 진입점. 모든 전이 트리거(게임 시작·첫 정답·포기 임계·퇴장 재평가·
 * sweeper 타임아웃·종료)가 여기로 수렴해 `RoundStateStore`의 원자 CAS를 통과한다. 전이 성공(CAS==TRANSITIONED)일 때만 후속
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
    private val scoreboardAssembler: RoundScoreboardAssembler,
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
     *
     * **포기 중인 참가자는 그 라운드의 정답 판정에서 제외된다.** 대가가 없으면 "일단 누르고 계속 추측"이
     * 손해 없는 지배 전략이 되어 매 라운드가 조기 종료되고, 임계가 의미를 잃는다. 취소하면 즉시 복원된다.
     * 이 게이트는 채팅 원문 방송에 영향을 주지 않는다 — 방송은 호출자가 이미 수행했고, 포기한 사람의
     * 잡담·반응은 그대로 살아 있어야 한다(빠지는 것은 게임이 아니라 그 라운드의 채점이다).
     */
    fun submitAnswer(roomId: Long, playerId: Long, content: String) {
        val snapshot = roundStateStore.snapshot(roomId) ?: return
        if (snapshot.phase != RoundPhase.OPEN) {
            return
        }
        // 정답 비교보다 앞에 둔다 — 포기자의 입력은 애초에 정답 후보가 아니다. 포기 여부 판정은
        // `SISMEMBER` 단독이 아니라 `passSeq == roundSeq` 유효성과 함께 하나의 원자 연산으로 읽는다
        // (불일치면 이전 라운드의 잔재이므로 포기 상태가 아니다).
        if (roundStateStore.isPassing(roomId, playerId)) {
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

    /**
     * 포기 신호 처리 — 토글 후 임계에 도달했으면 승자 없는 `REVEAL`로 전이하고, 아니면 현황만 전파한다.
     *
     * `expectedSeq`는 클라이언트가 보고 있던 라운드다. 마감 직전에 보낸 포기가 전이 직후에 도착하면
     * 아직 곡이 들리지도 않은 다음 라운드에 표가 꽂히므로, 불일치(`IGNORED`)·마감 이후(`NOT_DUE`)는
     * 아무것도 하지 않는다.
     */
    fun pass(roomId: Long, playerId: Long, expectedSeq: Long) {
        publishPassOutcome(roomId, roundStateStore.togglePass(roomId, expectedSeq, playerId))
    }

    /**
     * 게임 중 퇴장 처리 — 점수판·포기 집합에서 제거하고 임계를 재평가한다.
     *
     * 로스터가 줄면 임계도 함께 내려가므로, 아무도 새로 누르지 않았는데 임계가 충족되는 상태가 성립한다.
     * 재평가가 없으면 그 방은 마감까지 영영 대기한다.
     *
     * 호출 지점(`RoomLeftEvent`의 `AFTER_COMMIT` 핸들러)은 이미 트랜잭션 밖이라, 여기서 발행하는 라운드
     * 이벤트는 라운드 이벤트 리스너의 `fallbackExecution = true` 덕분에 성립한다.
     */
    fun onPlayerLeft(roomId: Long, playerId: Long) {
        publishPassOutcome(roomId, roundStateStore.onPlayerLeft(roomId, playerId))
    }

    /**
     * 재접속 복원용 스냅샷. `OPEN` 중에는 정답을 제외하고 `REVEAL`·`ENDED`에서만 포함한다.
     *
     * 포기 현황은 인원수와 **조회자 본인의 포기 여부**만 담는다 — 포기자 목록을 내리면 devtools에서
     * 그대로 보여 "누가 눌렀는지는 공개하지 않는다"는 결정이 무효가 된다.
     */
    fun getSnapshot(roomId: Long, playerId: Long): RoundSnapshotResponse? {
        val snapshot = roundStateStore.snapshot(roomId, playerId) ?: return null
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
        // 실시간 `ROUND_REVEALED`와 같은 조립기를 거친다 — 형태가 갈리면 재접속으로 복원한 화면만
        // 이름을 잃어, 이벤트를 받은 멤버와 다르게 보인다.
        val scoreboard = scoreboardAssembler.assemble(snapshot.scores, snapshot.winnerId)
        return RoundSnapshotResponse(
            phase = snapshot.phase,
            roundSeq = snapshot.roundSeq,
            roundNumber = snapshot.trackIndex + 1,
            totalRounds = snapshot.totalRounds,
            deadlineAt = snapshot.deadlineAt,
            currentTrack = RoundTrackRefResponse.of(track),
            title = if (revealed) track.title else null,
            winnerId = snapshot.winnerId,
            winnerNickname = scoreboard.winnerNickname,
            scores = scoreboard.entries,
            nextTrack = nextTrack,
            passedCount = snapshot.passedCount,
            requiredCount = snapshot.requiredCount,
            passed = snapshot.passing,
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

    /**
     * 포기 경로의 결과 전파. 전이됐으면 `ROUND_REVEALED`만 발행하고 포기 현황은 생략한다 — 둘이 함께
     * 나가면 클라이언트가 `REVEAL`로 넘어간 뒤 포기 카운트를 다시 그리게 된다. 상태가 바뀌지 않은
     * `IGNORED`·`NOT_DUE`는 아무것도 전파하지 않는다.
     */
    private fun publishPassOutcome(roomId: Long, outcome: PassOutcome) {
        val transition = outcome.transition
        if (transition.result == TransitionResult.TRANSITIONED) {
            publishRevealOf(roomId, transition)
            return
        }
        if (outcome.roundSeq == 0L) {
            return
        }
        eventPublisher.publishEvent(
            RoundPassUpdatedEvent(
                roomId = roomId,
                roundSeq = outcome.roundSeq,
                passedCount = outcome.passedCount,
                requiredCount = outcome.requiredCount,
            )
        )
    }

    /**
     * 전이 결과만으로 `ROUND_REVEALED`를 발행한다(승자 없음). 전이 전에 스냅샷을 읽지 않은 트리거용.
     *
     * `trackOrder`는 게임 시작 시 고정되므로 전이 뒤에 읽어도 안전하다 — 인덱스는 전이가 돌려준
     * `trackIndex`를 쓰므로 그 사이에 라운드가 더 진행되더라도 공개 대상 트랙이 흔들리지 않는다.
     */
    private fun publishRevealOf(roomId: Long, transition: RoundTransition) {
        val snapshot = roundStateStore.snapshot(roomId) ?: return
        val tracks = roomPlaylistTrackRepository.findByRoomId(roomId)
        val track = snapshot.trackOrder.getOrNull(transition.trackIndex)?.let { trackOf(tracks, it) } ?: return
        val nextTrack = snapshot.trackOrder.getOrNull(transition.trackIndex + 1)?.let { trackOf(tracks, it) }
        publishRoundRevealed(roomId, transition, track, nextTrack)
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
