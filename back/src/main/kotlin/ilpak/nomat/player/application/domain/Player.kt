package ilpak.nomat.player.application.domain

import ilpak.nomat.common.AuditDateMetadata
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.TableGenerator
import org.springframework.data.jpa.domain.support.AuditingEntityListener

@Entity
@EntityListeners(AuditingEntityListener::class)
class Player(
    var nickname: String,
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
) {
    val displayName: String
        get() = "$nickname#${registrationType.code}"

    companion object {
        const val MAX_NICKNAME_LENGTH = 40
    }
}
