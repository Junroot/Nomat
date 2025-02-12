package ilpak.nomat.player.out.jpa.entity

import ilpak.nomat.player.application.domain.Player
import ilpak.nomat.player.application.domain.RegistrationType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id

@Entity(name = "player")
class PlayerEntity(
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
