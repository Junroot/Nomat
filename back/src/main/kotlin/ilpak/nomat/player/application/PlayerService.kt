package ilpak.nomat.player.application

import ilpak.nomat.player.application.domain.PlayerRepository
import ilpak.nomat.player.application.domain.RegistrationType
import ilpak.nomat.player.application.dto.PlayerRequest
import ilpak.nomat.player.application.dto.PlayerResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PlayerService(
    private val playerRepository: PlayerRepository
) {

    fun exists(playerId: Long): Boolean {
        return playerRepository.existsById(playerId)
    }

    fun findAll(): List<PlayerResponse> {
        return playerRepository.findAll().map { PlayerResponse(it) }
    }

    fun findByRegistrationTypeAndRegistrationId(
        registrationType: RegistrationType,
        registrationId: String
    ): PlayerResponse? {
        return playerRepository.findByRegistrationTypeAndRegistrationId(registrationType, registrationId)
            ?.let { PlayerResponse(it) }
    }

    fun findByIdIn(ids: Set<Long>): List<PlayerResponse> {
        return playerRepository.findByIdIn(ids).map { PlayerResponse(it) }
    }

    @Transactional
    fun save(request: PlayerRequest): PlayerResponse {
        return PlayerResponse(playerRepository.save(request.toDomain()))
    }
}
