package ilpak.nomat.player.application.domain

interface PlayerRepository {

    fun existsById(id: Long): Boolean
    fun findAll(): List<Player>
    fun findByRegistrationTypeAndRegistrationId(registrationType: RegistrationType, registrationId: String): Player?
    fun findById(id: Long): Player?
    fun findByIdIn(ids: Set<Long>): List<Player>
    fun findByNicknameAndRegistrationType(nickname: String, registrationType: RegistrationType): Player?
    fun save(player: Player): Player
}
