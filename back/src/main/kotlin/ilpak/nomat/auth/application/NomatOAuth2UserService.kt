package ilpak.nomat.auth.application

import ilpak.nomat.auth.application.domain.NomatOAuth2User
import ilpak.nomat.player.application.PlayerService
import ilpak.nomat.player.application.domain.RegistrationType
import ilpak.nomat.player.application.dto.PlayerRequest
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class NomatOAuth2UserService(
    private val playerService: PlayerService,
) : DefaultOAuth2UserService() {

    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User? {
        val oAuth2User = super.loadUser(userRequest)
        val registrationType = getRegistrationType(userRequest) ?: return null
        val registrationId = getRegistrationId(registrationType, oAuth2User) ?: return null

        val player = playerService.findByRegistrationTypeAndRegistrationId(registrationType, registrationId)
            ?: playerService.save(
                PlayerRequest(
                    getDefaultNickname(registrationType, oAuth2User) ?: "player",
                    registrationType,
                    registrationId
                )
            )

        return NomatOAuth2User(player.id, oAuth2User)
    }

    private fun getRegistrationType(userRequest: OAuth2UserRequest): RegistrationType? {
        return when (userRequest.clientRegistration.registrationId) {
            "discord" -> RegistrationType.DISCORD
            else -> null
        }
    }

    private fun getRegistrationId(registrationType: RegistrationType, oAuth2User: OAuth2User): String? {
        return when (registrationType) {
            RegistrationType.DISCORD -> oAuth2User.attributes["id"] as? String
        }
    }

    private fun getDefaultNickname(registrationType: RegistrationType, oAuth2User: OAuth2User): String? {
        return when (registrationType) {
            RegistrationType.DISCORD -> oAuth2User.attributes["username"] as? String
        }
    }
}
