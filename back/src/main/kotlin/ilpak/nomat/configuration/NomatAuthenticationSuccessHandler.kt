package ilpak.nomat.configuration

import ilpak.nomat.auth.NomatOAuth2User
import ilpak.nomat.auth.TokenService
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.web.DefaultRedirectStrategy
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class NomatAuthenticationSuccessHandler(
    @Value("\${jwt.domain}")
    private val domain: String,
    private val tokenService: TokenService,
) : AuthenticationSuccessHandler {

    private val redirectStrategy = DefaultRedirectStrategy()

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val nomatOauth2User = authentication.principal as NomatOAuth2User
        val token = tokenService.getNewToken(nomatOauth2User.playerId)
        response.addCookie(
            Cookie(TokenService.TOKEN_COOKIE_KEY, token).also {
                it.maxAge = Duration.ofDays(TokenService.EXPIRATION_DAYS.toLong()).toSeconds().toInt()
                it.path = "/"
                it.isHttpOnly = true
                it.domain = domain
            }
        )

        redirectStrategy.sendRedirect(request, response, "/html/close.html")
    }
}
