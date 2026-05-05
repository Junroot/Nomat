package ilpak.nomat.playlist.application.domain

import ilpak.nomat.common.metadata.AuditMetadata
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.TableGenerator
import org.springframework.data.domain.AbstractAggregateRoot
import org.springframework.data.jpa.domain.support.AuditingEntityListener

@Entity
@EntityListeners(AuditingEntityListener::class)
class Playlist(
    var title: String,
    var description: String,
    @Embedded
    val auditMetadata: AuditMetadata = AuditMetadata(),
    @Id
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "playlist_id_generator")
    @TableGenerator(
        name = "playlist_id_generator",
        table = "hibernate_sequences",
        pkColumnName = "sequence_name",
        pkColumnValue = "playlist",
        allocationSize = 1000,
    )
    val id: Long = 0L,
) : AbstractAggregateRoot<Playlist>() {

    val masterId: Long
        get() = auditMetadata.createdBy

    fun update(title: String, description: String) {
        this.title = title
        this.description = description
        registerEvent(PlaylistUpserted.from(this))
    }

    fun markDeleted() {
        registerEvent(PlaylistDeleted(id))
    }

    @PrePersist
    @Suppress("unused")
    protected fun registerUpsertedOnPersist() {
        registerEvent(PlaylistUpserted.from(this))
    }

    companion object {
        const val MAX_TITLE_LENGTH = 100
        const val MAX_DESCRIPTION_LENGTH = 500
        const val MAX_TRACK_COUNT = 1000
        const val MAX_PLAYLIST_COUNT_PER_PLAYER = 1000
    }
}
