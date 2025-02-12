package ilpak.nomat.player.out

import ilpak.nomat.player.application.domain.Player
import ilpak.nomat.player.application.domain.PlayerRepository
import ilpak.nomat.player.application.domain.RegistrationType
import ilpak.nomat.player.out.jpa.PlayerJpaRepository
import ilpak.nomat.player.out.jpa.entity.PlayerEntity
import org.springframework.stereotype.Repository

@Repository
class PlayerRepositoryImpl(
    private val playerJpaRepository: PlayerJpaRepository
) : PlayerRepository {

    override fun existsById(id: Long): Boolean {
        return playerJpaRepository.existsById(id)
    }

    override fun findByRegistrationTypeAndRegistrationId(
        registrationType: RegistrationType,
        registrationId: String
    ): Player? {
        return playerJpaRepository.findByRegistrationTypeAndRegistrationId(registrationType, registrationId)?.toDomain()
    }

    override fun save(player: Player): Player {
        return playerJpaRepository.save(PlayerEntity(player)).toDomain()
    }

}
