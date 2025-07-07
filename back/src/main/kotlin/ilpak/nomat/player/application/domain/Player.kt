package ilpak.nomat.player.application.domain

import jakarta.persistence.*

@Entity
class Player(
	val nickname: String,
	@Column(columnDefinition = "varchar(20)")
	@Enumerated(value = EnumType.STRING)
	val registrationType: RegistrationType,
	val registrationId: String,
	@Id
	@GeneratedValue(strategy = GenerationType.TABLE, generator = "player_id_generator")
	@TableGenerator(
		name = "player_id_generator",
		table = "hibernate_sequences",
		pkColumnName = "sequence_name",
		pkColumnValue = "player",
		allocationSize = 1000,
	)
	val id: Long = 0,
)
