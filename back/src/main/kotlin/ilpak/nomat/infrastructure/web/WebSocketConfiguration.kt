package ilpak.nomat.infrastructure.web

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import org.springframework.web.socket.server.HandshakeInterceptor

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfiguration(
    private val jwtHandshakeInterceptor: HandshakeInterceptor,
    private val roomJoinChannelInterceptor: RoomJoinChannelInterceptor,
    @Value("\${app.cors.origins}") private val origins: String,
) : WebSocketMessageBrokerConfigurer {

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws")
            .setAllowedOrigins(origins)
            .addInterceptors(jwtHandshakeInterceptor)
    }

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic")
        registry.setApplicationDestinationPrefixes("/app")
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(roomJoinChannelInterceptor)
    }
}
