package ilpak.nomat.player.out.jpa

import ilpak.nomat.player.application.domain.RegistrationType
import ilpak.nomat.player.out.jpa.entity.PlayerEntity
import org.springframework.data.repository.CrudRepository

interface PlayerJpaRepository : CrudRepository<PlayerEntity, Long> {

    fun findByRegistrationTypeAndRegistrationId(
        registrationType: RegistrationType,
        registrationId: String
    ): PlayerEntity?
}
