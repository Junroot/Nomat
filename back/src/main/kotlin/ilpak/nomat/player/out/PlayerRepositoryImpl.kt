package ilpak.nomat.player.out

import ilpak.nomat.player.application.domain.Player
import ilpak.nomat.player.application.domain.PlayerRepository
import ilpak.nomat.player.application.domain.RegistrationType
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
private class PlayerRepositoryImpl(
    private val playerJpaRepository: PlayerJpaRepository
) : PlayerRepository {

    override fun existsById(id: Long): Boolean {
        return playerJpaRepository.existsById(id)
    }

    override fun findAll(): List<Player> {
        return playerJpaRepository.findAll().toList()
    }

    override fun findByRegistrationTypeAndRegistrationId(
        registrationType: RegistrationType,
        registrationId: String
    ): Player? {
        return playerJpaRepository.findByRegistrationTypeAndRegistrationId(registrationType, registrationId)
    }

    override fun findById(id: Long): Player? {
        return playerJpaRepository.findByIdOrNull(id)
    }

    override fun findByIdIn(ids: Set<Long>): List<Player> {
        return playerJpaRepository.findByIdIn(ids)
    }

    override fun findByNickname(nickname: String): Player? {
        return playerJpaRepository.findByNickname(nickname)
    }

    override fun save(player: Player): Player {
        return playerJpaRepository.save(player)
    }
}

private interface PlayerJpaRepository : CrudRepository<Player, Long> {

    fun findByRegistrationTypeAndRegistrationId(
        registrationType: RegistrationType,
        registrationId: String
    ): Player?

    fun findByIdIn(ids: Set<Long>): List<Player>
    fun findByNickname(nickname: String): Player?
}
