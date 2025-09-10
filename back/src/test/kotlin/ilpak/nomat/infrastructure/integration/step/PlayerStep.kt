package ilpak.nomat.infrastructure.integration.step

import ilpak.nomat.player.application.PlayerService
import ilpak.nomat.player.application.domain.RegistrationType
import ilpak.nomat.player.application.dto.PlayerRequest
import ilpak.nomat.player.application.dto.PlayerResponse
import org.springframework.boot.test.context.TestComponent

fun dummyPlayerRequest(
    nickname: String = "testUser",
    registrationType: RegistrationType = RegistrationType.DISCORD,
    registrationId: String = "testRegistrationId"
): PlayerRequest = PlayerRequest(
    nickname = nickname,
    registrationType = registrationType,
    registrationId = registrationId
)

@TestComponent
class PlayerStep(
    private val playerService: PlayerService
) {

    fun save(request: PlayerRequest): PlayerResponse {
        return playerService.save(request)
    }
}
