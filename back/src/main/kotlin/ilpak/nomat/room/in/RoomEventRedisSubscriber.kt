package ilpak.nomat.room.`in`

import com.fasterxml.jackson.databind.ObjectMapper
import ilpak.nomat.room.application.dto.RoomJoinedEventMessage
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.listener.PatternTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

@Component
private class RoomEventRedisSubscriber(
    private val messagingTemplate: SimpMessagingTemplate,
    private val objectMapper: ObjectMapper,
) : MessageListener {

    override fun onMessage(message: Message, pattern: ByteArray?) {
        val event = objectMapper.readValue(message.body, RoomJoinedEventMessage::class.java)
        messagingTemplate.convertAndSend("/topic/rooms/${event.roomId}", event)
    }
}

@Configuration
private class RoomEventRedisSubscriberConfiguration {

    @Bean
    fun roomEventRedisMessageListenerContainer(
        connectionFactory: RedisConnectionFactory,
        roomEventRedisSubscriber: MessageListener,
    ): RedisMessageListenerContainer {
        val container = RedisMessageListenerContainer()
        container.setConnectionFactory(connectionFactory)
        container.addMessageListener(roomEventRedisSubscriber, PatternTopic("room:*:events"))
        return container
    }
}
