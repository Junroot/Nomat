package ilpak.nomat.infrastructure.integration.util

import com.fasterxml.jackson.databind.ObjectMapper
import ilpak.nomat.auth.application.TokenService
import ilpak.nomat.player.application.dto.PlayerResponse
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import java.util.concurrent.TimeUnit

fun connectStomp(
    objectMapper: ObjectMapper,
    tokenService: TokenService,
    port: Int,
    player: PlayerResponse,
    roomId: Long,
    password: String?,
): StompSession {
    val stompClient = WebSocketStompClient(StandardWebSocketClient())
    stompClient.messageConverter = MappingJackson2MessageConverter(objectMapper)

    val stompHeaders = StompHeaders()
    stompHeaders.add("roomId", roomId.toString())
    if (password != null) {
        stompHeaders.add("password", password)
    }

    val httpHeaders = WebSocketHttpHeaders()
    val token = tokenService.getNewToken(player.id)
    httpHeaders.add("Cookie", "${TokenService.TOKEN_COOKIE_KEY}=$token")

    return stompClient.connectAsync(
        "ws://localhost:$port/ws",
        httpHeaders,
        stompHeaders,
        object : StompSessionHandlerAdapter() {}
    ).get(5, TimeUnit.SECONDS)
}
