package ilpak.nomat.infrastructure.websocket

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

@Configuration
class WebSocketArgumentResolverConfig : WebSocketMessageBrokerConfigurer {

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(NomatAuthenticationPrincipalArgumentResolver())
    }
}
