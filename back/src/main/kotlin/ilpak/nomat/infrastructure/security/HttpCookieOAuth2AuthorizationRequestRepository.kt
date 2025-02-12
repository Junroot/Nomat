package ilpak.nomat.infrastructure.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import ilpak.nomat.auth.application.dto.NomatOAuth2AuthorizationRequest
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import org.springframework.stereotype.Component
import org.springframework.web.util.WebUtils
import java.util.*


@Component
class HttpCookieOAuth2AuthorizationRequestRepository(
    private val objectMapper: ObjectMapper
) : AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    override fun loadAuthorizationRequest(request: HttpServletRequest): OAuth2AuthorizationRequest? {
        return WebUtils.getCookie(request, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME)
            ?.let { cookie ->
                val cookieValue = objectMapper.readValue<NomatOAuth2AuthorizationRequest>(
                    Base64.getDecoder().decode(cookie.value.toByteArray())
                )
                cookieValue.toOAuth2AuthorizationRequest()
            }
    }

    override fun removeAuthorizationRequest(
        request: HttpServletRequest,
        response: HttpServletResponse
    ): OAuth2AuthorizationRequest? {

        val authorizationRequest = loadAuthorizationRequest(request) ?: return null

        saveAuthorizationRequest(null, request, response)
        return authorizationRequest
    }

    override fun saveAuthorizationRequest(
        authorizationRequest: OAuth2AuthorizationRequest?,
        request: HttpServletRequest,
        response: HttpServletResponse
    ) {
        if (authorizationRequest == null) {
            response.addCookie(
                Cookie(OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, "")
                    .also {
                        it.isHttpOnly = true
                        it.path = "/"
                        it.maxAge = 0
                    }
            )
            return
        }

        val cookieValue =
            Base64.getEncoder().encodeToString(
                objectMapper.writeValueAsBytes(
                    NomatOAuth2AuthorizationRequest.from(authorizationRequest)
                )
            )
        response.addCookie(
            Cookie(OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, cookieValue)
                .also {
                    it.isHttpOnly = true
                    it.path = "/"
                    it.maxAge = COOKIE_EXPIRE_SECONDS
                }
        )
    }

    companion object {
        const val OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME: String = "oauth2_auth_request"
        private const val COOKIE_EXPIRE_SECONDS = 180
    }
}
