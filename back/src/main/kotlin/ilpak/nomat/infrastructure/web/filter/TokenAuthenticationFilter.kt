package ilpak.nomat.infrastructure.web.filter

import ilpak.nomat.auth.application.TokenService
import ilpak.nomat.player.application.PlayerService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.WebUtils

@Component
class TokenAuthenticationFilter(private val tokenService: TokenService) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = WebUtils.getCookie(request, TokenService.TOKEN_COOKIE_KEY)?.value
        val playerId = token?.let { tokenService.getPlayerId(it) }
        if (playerId != null) {
            val authentication = UsernamePasswordAuthenticationToken.authenticated(playerId, token, emptyList())
            SecurityContextHolder.getContext().authentication = authentication
            MDC.put("requestPlayerId", playerId.toString())
        }

        filterChain.doFilter(request, response)
    }
}
