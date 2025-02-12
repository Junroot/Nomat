package ilpak.nomat.player.application.domain

interface PlayerRepository {

    fun existsById(id: Long): Boolean
    fun findAll(): List<Player>
    fun findByRegistrationTypeAndRegistrationId(registrationType: RegistrationType, registrationId: String): Player?
    fun save(player: Player): Player
}
