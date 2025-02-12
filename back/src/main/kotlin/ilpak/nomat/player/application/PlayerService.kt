package ilpak.nomat.player.application

import ilpak.nomat.player.application.domain.PlayerRepository
import ilpak.nomat.player.application.domain.RegistrationType
import ilpak.nomat.player.application.dto.PlayerRequest
import ilpak.nomat.player.application.dto.PlayerResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PlayerService(
    private val playerRepository: PlayerRepository
) {

    @Transactional(readOnly = true)
    fun exists(playerId: Long): Boolean {
        return playerRepository.existsById(playerId)
    }

    @Transactional(readOnly = true)
    fun findByRegistrationTypeAndRegistrationId(
        registrationType: RegistrationType,
        registrationId: String
    ): PlayerResponse? {
        return playerRepository.findByRegistrationTypeAndRegistrationId(registrationType, registrationId)
            ?.let { PlayerResponse(it) }
    }

    fun save(request: PlayerRequest): PlayerResponse {
        return PlayerResponse(playerRepository.save(request.toDomain()))
    }
}
