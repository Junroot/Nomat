package ilpak.nomat.infrastructure.web

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import org.springframework.web.socket.server.HandshakeInterceptor

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfiguration(
    private val jwtHandshakeInterceptor: HandshakeInterceptor,
    private val roomJoinChannelInterceptor: RoomJoinChannelInterceptor,
    private val stompErrorHandler: StompErrorHandler,
    @Value("\${app.cors.origins}") private val origins: String,
) : WebSocketMessageBrokerConfigurer {

    /**
     * STOMP 하트비트 전용 스케줄러. `SimpleBrokerRegistration`은 `TaskScheduler`가 없으면 하트비트를 `0,0`으로
     * 협상해 양방향 모두 끄므로, 켜려면 스케줄러를 줘야 한다.
     *
     * 이름을 `taskScheduler`로 두면 안 된다 — 그 이름은 `@Scheduled` 전용 풀(`SchedulingConfiguration`)이 쓰며,
     * `TaskScheduler` 빈이 여럿일 때 Spring이 그 이름으로 `@Scheduled` 스케줄러를 고른다. 기존 `messageBrokerTaskScheduler`를
     * 주입해 재사용하지 않는 이유는 그 빈이 이 설정 클래스의 부모 설정에서 만들어져 순환 참조가 생기기 쉽기 때문이다.
     */
    @Bean(name = ["wsHeartbeatTaskScheduler"])
    fun wsHeartbeatTaskScheduler(): ThreadPoolTaskScheduler = ThreadPoolTaskScheduler().apply {
        poolSize = 1
        setThreadNamePrefix("ws-heartbeat-")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws")
            .setAllowedOrigins(origins)
            .addInterceptors(jwtHandshakeInterceptor)
        registry.setErrorHandler(stompErrorHandler)
    }

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        // 서버 송신 10초 / 클라이언트 수신 10초. 클라이언트 프레임이 협상 간격의 3배(≈30초) 동안 없으면 서버가 세션을 닫고,
        // 그 끊김은 일반 끊김과 같은 SessionDisconnectEvent 경로로 합류한다. 유휴 연결도 10초마다 프레임이 흘러
        // 프록시 유휴 타임아웃에 잘리지 않는다. 클라이언트(@stomp/stompjs)의 기본값 10초/10초와 그대로 협상된다.
        registry.enableSimpleBroker("/topic")
            .setHeartbeatValue(longArrayOf(HEARTBEAT_MILLIS, HEARTBEAT_MILLIS))
            .setTaskScheduler(wsHeartbeatTaskScheduler())
        registry.setApplicationDestinationPrefixes("/app")
    }

    companion object {
        private const val HEARTBEAT_MILLIS = 10_000L
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(roomJoinChannelInterceptor)
    }
}
