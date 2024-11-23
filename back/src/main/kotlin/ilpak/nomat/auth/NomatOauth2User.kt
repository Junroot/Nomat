package ilpak.nomat.auth

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.core.user.OAuth2User

class NomatOauth2User(
    val playerId: Long,
    private val defaultOAuth2User: OAuth2User,
) : OAuth2User {
    override fun getName(): String {
        return defaultOAuth2User.name
    }

    override fun getAttributes(): Map<String, Any> {
        return defaultOAuth2User.attributes
    }

    override fun getAuthorities(): Collection<GrantedAuthority> {
        return defaultOAuth2User.authorities
    }
}
