package ilpak.nomat.infrastructure.integration

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class TestAuthenticationFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val playerId = request.getHeader("playerId")?.toLongOrNull()
        if (playerId != null) {
            val authentication = UsernamePasswordAuthenticationToken.authenticated(playerId, playerId, emptyList())
            SecurityContextHolder.getContext().authentication = authentication
            MDC.put("requestPlayerId", playerId.toString())
        }

        filterChain.doFilter(request, response)
    }
}
