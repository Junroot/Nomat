package ilpak.nomat.room.`in`

import com.fasterxml.jackson.databind.ObjectMapper
import ilpak.nomat.player.application.PlayerService
import ilpak.nomat.room.application.RoundScoreboardAssembler
import ilpak.nomat.room.application.RoundService
import ilpak.nomat.room.application.domain.GameEndedEvent
import ilpak.nomat.room.application.domain.GameStartedEvent
import ilpak.nomat.room.application.domain.RoomJoinedEvent
import ilpak.nomat.room.application.domain.RoomLeftEvent
import ilpak.nomat.room.application.domain.RoundPassUpdatedEvent
import ilpak.nomat.room.application.domain.RoundRevealedEvent
import ilpak.nomat.room.application.domain.RoundStartedEvent
import ilpak.nomat.room.application.dto.GameEndedEventMessage
import ilpak.nomat.room.application.dto.GameStartedEventMessage
import ilpak.nomat.room.application.dto.RoomEventMessage
import ilpak.nomat.room.application.dto.RoomJoinedEventMessage
import ilpak.nomat.room.application.dto.RoomLeftEventMessage
import ilpak.nomat.room.application.dto.RoundPassUpdatedEventMessage
import ilpak.nomat.room.application.dto.RoundRevealedEventMessage
import ilpak.nomat.room.application.dto.RoundStartedEventMessage
import ilpak.nomat.room.application.dto.RoundTrackRefResponse
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
private class RoomEventListener(
    private val playerService: PlayerService,
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val roundService: RoundService,
    private val scoreboardAssembler: RoundScoreboardAssembler,
) {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleRoomJoined(event: RoomJoinedEvent) {
        val player = playerService.findById(event.playerId)
        val channel = RoomEventMessage.channelFor(event.roomId)
        val message = objectMapper.writeValueAsString(
            RoomJoinedEventMessage(
                roomId = event.roomId,
                playerId = event.playerId,
                nickname = player.nickname,
            )
        )
        redisTemplate.convertAndSend(channel, message)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleRoomLeft(event: RoomLeftEvent) {
        roundService.onPlayerLeft(event.roomId, event.playerId)
        val player = playerService.findById(event.playerId)
        val channel = RoomEventMessage.channelFor(event.roomId)
        val message = objectMapper.writeValueAsString(
            RoomLeftEventMessage(
                roomId = event.roomId,
                playerId = event.playerId,
                nickname = player.nickname,
            )
        )
        redisTemplate.convertAndSend(channel, message)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleGameStarted(event: GameStartedEvent) {
        val player = playerService.findById(event.playerId)
        val channel = RoomEventMessage.channelFor(event.roomId)
        val message = objectMapper.writeValueAsString(
            GameStartedEventMessage(
                roomId = event.roomId,
                playerId = event.playerId,
                nickname = player.nickname,
            )
        )
        redisTemplate.convertAndSend(channel, message)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleGameEnded(event: GameEndedEvent) {
        val nickname = event.playerId?.let { playerService.findById(it).nickname }
        val channel = RoomEventMessage.channelFor(event.roomId)
        val message = objectMapper.writeValueAsString(
            GameEndedEventMessage(
                roomId = event.roomId,
                playerId = event.playerId,
                nickname = nickname,
            )
        )
        redisTemplate.convertAndSend(channel, message)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun handleRoundStarted(event: RoundStartedEvent) {
        val channel = RoomEventMessage.channelFor(event.roomId)
        val message = objectMapper.writeValueAsString(
            RoundStartedEventMessage(
                roomId = event.roomId,
                roundSeq = event.roundSeq,
                roundNumber = event.roundNumber,
                totalRounds = event.totalRounds,
                deadlineAt = event.deadlineAt,
                embedId = event.embedId,
                startTimeSec = event.startTimeSec,
                endTimeSec = event.endTimeSec,
                repeatCount = event.repeatCount,
            )
        )
        redisTemplate.convertAndSend(channel, message)
    }

    // 라운드 전이는 트랜잭션 밖에서 일어나므로 `fallbackExecution`이 필요하다
    // (`handleRoundStarted`·`handleRoundRevealed`와 같은 이유).
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun handleRoundPassUpdated(event: RoundPassUpdatedEvent) {
        val channel = RoomEventMessage.channelFor(event.roomId)
        val message = objectMapper.writeValueAsString(
            RoundPassUpdatedEventMessage(
                roomId = event.roomId,
                roundSeq = event.roundSeq,
                passedCount = event.passedCount,
                requiredCount = event.requiredCount,
            )
        )
        redisTemplate.convertAndSend(channel, message)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun handleRoundRevealed(event: RoundRevealedEvent) {
        // 닉네임은 `room` 애그리거트가 알지 못하는 표현 관심사라 도메인 이벤트가 아니라 여기서 붙인다
        // — `handleRoomJoined`·`handleRoomLeft`·`handleGameStarted`가 이미 쓰는 방식이다.
        val scoreboard = scoreboardAssembler.assemble(event.scores, event.winnerId)
        val channel = RoomEventMessage.channelFor(event.roomId)
        val message = objectMapper.writeValueAsString(
            RoundRevealedEventMessage(
                roomId = event.roomId,
                roundSeq = event.roundSeq,
                winnerId = event.winnerId,
                winnerNickname = scoreboard.winnerNickname,
                title = event.title,
                scores = scoreboard.entries,
                nextTrack = event.nextTrack?.let {
                    RoundTrackRefResponse(it.embedId, it.startTimeSec, it.endTimeSec, it.repeatCount)
                },
            )
        )
        redisTemplate.convertAndSend(channel, message)
    }
}
