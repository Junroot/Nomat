package ilpak.nomat.playlist.application.domain

import ilpak.nomat.common.metadata.AuditMetadata
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.TableGenerator
import org.hibernate.annotations.BatchSize
import org.springframework.data.jpa.domain.support.AuditingEntityListener

@Entity
@EntityListeners(AuditingEntityListener::class)
class Track(
    val embedId: String,
    var title: String,
    var startTimeSec: Int,
    var endTimeSec: Int,
    var repeatCount: Int,
    @ElementCollection(fetch = FetchType.EAGER)
    @BatchSize(size = 10)
    @CollectionTable(name = "track_additional_title", joinColumns = [JoinColumn(name = "track_id")])
    @Column(name = "additional_title")
    var additionalTitles: Set<String>,
    @ManyToOne
    @JoinColumn(name = "playlist_id")
    val playlist: Playlist,
    @Column(name = "is_representative")
    var representative: Boolean,
    @Embedded
    val auditMetadata: AuditMetadata = AuditMetadata(),
    @Id
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "track_id_generator")
    @TableGenerator(
        name = "track_id_generator",
        table = "hibernate_sequences",
        pkColumnName = "sequence_name",
        pkColumnValue = "track",
        allocationSize = 1000,
    )
    val id: Long = 0L,
) {

    companion object {
        const val MAX_EMBED_ID_LENGTH = 20
        const val MAX_TITLE_LENGTH = 100
        const val MAX_REPEAT_COUNT = 5
        const val MAX_ADDITIONAL_TITLE_COUNT = 10
    }
}
