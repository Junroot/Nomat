package ilpak.nomat.infrastructure.web

import ilpak.nomat.auth.application.TokenService
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor
import org.springframework.web.util.WebUtils

@Component
class JwtHandshakeInterceptor(
    private val tokenService: TokenService,
) : HandshakeInterceptor {

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        if (request !is ServletServerHttpRequest) return false
        val token = WebUtils.getCookie(request.servletRequest, TokenService.TOKEN_COOKIE_KEY)?.value
            ?: return false
        val playerId = tokenService.getPlayerId(token) ?: return false
        attributes[PLAYER_ID_KEY] = playerId
        return true
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?,
    ) {
    }

    companion object {
        const val PLAYER_ID_KEY = "playerId"
    }
}
