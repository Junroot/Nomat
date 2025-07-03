package ilpak.nomat.player.application.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.hibernate.annotations.GenericGenerator
import org.hibernate.annotations.Parameter

@Entity
class Player(
    val nickname: String,
    @Column(columnDefinition = "varchar(20)")
    @Enumerated(value = EnumType.STRING)
    val registrationType: RegistrationType,
    val registrationId: String,
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "player_id_generator")
	@GenericGenerator(
		name = "player_id_generator",
		strategy = "sequence",
		parameters = [
			Parameter(name = "sequence_name", value = "player_sequence"),
			Parameter(name = "increment_size", value = "1000"),
			Parameter(name = "optimizer", value = "pooled-lotl"),
		]
	)
    val id: Long = 0,
)
