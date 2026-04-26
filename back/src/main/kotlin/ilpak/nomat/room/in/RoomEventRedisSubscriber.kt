package ilpak.nomat.room.`in`

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import ilpak.nomat.room.application.dto.RoomEventMessage
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.data.redis.listener.PatternTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

private val log = LoggerFactory.getLogger(RoomEventRedisSubscriber::class.java)

@Component
private class RoomEventRedisSubscriber(
    private val messagingTemplate: SimpMessagingTemplate,
    private val objectMapper: ObjectMapper,
    private val listenerContainer: RedisMessageListenerContainer,
) : MessageListener {

    @PostConstruct
    fun registerListener() {
        listenerContainer.addMessageListener(this, PatternTopic(RoomEventMessage.CHANNEL_PATTERN))
    }

    override fun onMessage(message: Message, pattern: ByteArray?) {
        val body = String(message.body)
        log.info("[DIAG] Redis onMessage channel={} body={}", String(message.channel), body)
        val event = objectMapper.readValue<RoomEventMessage>(message.body)
        val destination = "/topic/rooms/${event.roomId}"
        log.info("[DIAG] Broadcasting to STOMP destination={}", destination)
        messagingTemplate.convertAndSend(destination, event)
        log.info("[DIAG] STOMP broadcast DONE")
    }
}
