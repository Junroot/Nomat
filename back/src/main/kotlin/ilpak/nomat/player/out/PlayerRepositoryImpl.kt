package ilpak.nomat.player.out

import ilpak.nomat.player.application.domain.Player
import ilpak.nomat.player.application.domain.PlayerRepository
import ilpak.nomat.player.application.domain.RegistrationType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
private class PlayerRepositoryImpl(
    private val playerJpaRepository: PlayerJpaRepository
) : PlayerRepository {

    override fun existsById(id: Long): Boolean {
        return playerJpaRepository.existsById(id)
    }

    override fun findAll(): List<Player> {
        return playerJpaRepository.findAll().map { it.toDomain() }
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

private interface PlayerJpaRepository : CrudRepository<PlayerEntity, Long> {

    fun findByRegistrationTypeAndRegistrationId(
        registrationType: RegistrationType,
        registrationId: String
    ): PlayerEntity?
}

@Entity(name = "player")
private class PlayerEntity(
    val nickname: String,
    @Column(columnDefinition = "varchar(20)")
    @Enumerated(value = EnumType.STRING)
    val registrationType: RegistrationType,
    val registrationId: String,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
) {
    constructor(player: Player) : this(
        player.nickname,
        player.registrationType,
        player.registrationId,
        player.id
    )

    fun toDomain(): Player {
        return Player(
            nickname,
            registrationType,
            registrationId,
            id
        )
    }
}
