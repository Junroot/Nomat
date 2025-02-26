package ilpak.nomat.player.application.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id

@Entity
class Player(
    val nickname: String,
    @Column(columnDefinition = "varchar(20)")
    @Enumerated(value = EnumType.STRING)
    val registrationType: RegistrationType,
    val registrationId: String,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
)
