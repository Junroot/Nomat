package ilpak.nomat.configuration.filter

import ilpak.nomat.auth.TokenService
import ilpak.nomat.player.service.PlayerService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.WebUtils

@Component
class TokenAuthenticationFilter(
    private val tokenService: TokenService,
    private val playerService: PlayerService,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = WebUtils.getCookie(request, TokenService.TOKEN_COOKIE_KEY)?.value
        val playerId = token?.let { tokenService.getPlayerId(it) }
        if (playerId != null && playerService.exists(playerId)) {
            val authentication = UsernamePasswordAuthenticationToken.authenticated(playerId, token, emptyList())
            SecurityContextHolder.getContext().authentication = authentication
        }

        filterChain.doFilter(request, response)
    }
}
