package ilpak.nomat.room.application.domain

import ilpak.nomat.common.metadata.AuditMetadata
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
class Room(
    val title: String,
    val password: String?,
    val maxEntriesCount: Int,
    @Embedded
    val playlist: RoomPlaylist,
    @Embedded
    val auditMetadata: AuditMetadata = AuditMetadata(),
    @Id
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "room_id_generator")
    @TableGenerator(
        name = "room_id_generator",
        table = "hibernate_sequences",
        pkColumnName = "sequence_name",
        pkColumnValue = "room",
        allocationSize = 1000,
    )
    val id: Long = 0,
) {

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "CHAR(20) NOT NULL")
    var status: RoomStatus = RoomStatus.PENDING

    val playlistMasterId: Long
        get() = playlist.masterId

    companion object {
        const val MAX_TITLE_LENGTH = 30
        const val MAX_PASSWORD_LENGTH = 30
        const val MAX_MAX_ENTRIES_COUNT = 20
    }
}
