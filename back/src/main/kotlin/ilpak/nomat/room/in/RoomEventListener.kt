package ilpak.nomat.room.`in`

import com.fasterxml.jackson.databind.ObjectMapper
import ilpak.nomat.player.application.PlayerService
import ilpak.nomat.room.application.RoundService
import ilpak.nomat.room.application.domain.GameEndedEvent
import ilpak.nomat.room.application.domain.GameStartedEvent
import ilpak.nomat.room.application.domain.RoomJoinedEvent
import ilpak.nomat.room.application.domain.RoomLeftEvent
import ilpak.nomat.room.application.domain.RoundRevealedEvent
import ilpak.nomat.room.application.domain.RoundStartedEvent
import ilpak.nomat.room.application.dto.GameEndedEventMessage
import ilpak.nomat.room.application.dto.GameStartedEventMessage
import ilpak.nomat.room.application.dto.RoomEventMessage
import ilpak.nomat.room.application.dto.RoomJoinedEventMessage
import ilpak.nomat.room.application.dto.RoomLeftEventMessage
import ilpak.nomat.room.application.dto.RoundRevealedEventMessage
import ilpak.nomat.room.application.dto.RoundStartedEventMessage
import ilpak.nomat.room.application.dto.ScoreEntryResponse
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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun handleRoundRevealed(event: RoundRevealedEvent) {
        val channel = RoomEventMessage.channelFor(event.roomId)
        val message = objectMapper.writeValueAsString(
            RoundRevealedEventMessage(
                roomId = event.roomId,
                roundSeq = event.roundSeq,
                winnerId = event.winnerId,
                title = event.title,
                scores = event.scores.map { ScoreEntryResponse(it.playerId, it.score) },
            )
        )
        redisTemplate.convertAndSend(channel, message)
    }
}
