package ilpak.nomat.player.application.domain

import ilpak.nomat.common.AuditDateMetadata
import jakarta.persistence.*
import org.springframework.data.jpa.domain.support.AuditingEntityListener

@Entity
@EntityListeners(AuditingEntityListener::class)
class Player(
	val nickname: String,
	@Column(columnDefinition = "varchar(20)")
	@Enumerated(value = EnumType.STRING)
	val registrationType: RegistrationType,
	val registrationId: String,
	@Embedded
	val auditDateMetadata: AuditDateMetadata = AuditDateMetadata(),
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
