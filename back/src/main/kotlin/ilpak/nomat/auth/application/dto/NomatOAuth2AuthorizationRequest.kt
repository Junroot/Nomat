package ilpak.nomat.auth.application.dto

import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest

data class NomatOAuth2AuthorizationRequest(
    val authorizationUri: String,
    val clientId: String,
    val redirectUri: String,
    val scopes: Set<String>,
    val state: String,
    val additionalParameters: Map<String, Any>,
    val authorizationRequestUri: String,
    val attributes: Map<String, Any>,
) {

    fun toOAuth2AuthorizationRequest(): OAuth2AuthorizationRequest {
        return OAuth2AuthorizationRequest.authorizationCode()
            .authorizationUri(authorizationUri)
            .clientId(clientId)
            .redirectUri(redirectUri)
            .scopes(scopes)
            .state(state)
            .additionalParameters(additionalParameters)
            .authorizationRequestUri(authorizationRequestUri)
            .attributes(attributes)
            .build()
    }

    companion object {

        fun from(oAuth2AuthorizationRequest: OAuth2AuthorizationRequest): NomatOAuth2AuthorizationRequest {
            return NomatOAuth2AuthorizationRequest(
                oAuth2AuthorizationRequest.authorizationUri,
                oAuth2AuthorizationRequest.clientId,
                oAuth2AuthorizationRequest.redirectUri,
                oAuth2AuthorizationRequest.scopes,
                oAuth2AuthorizationRequest.state,
                oAuth2AuthorizationRequest.additionalParameters,
                oAuth2AuthorizationRequest.authorizationRequestUri,
                oAuth2AuthorizationRequest.attributes
            )
        }
    }
}
