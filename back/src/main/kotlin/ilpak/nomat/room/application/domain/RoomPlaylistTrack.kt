package ilpak.nomat.room.application.domain

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.IdClass
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import org.hibernate.annotations.BatchSize
import java.io.Serializable

@Entity
@IdClass(RoomPlaylistTrackId::class)
class RoomPlaylistTrack(
    val embedId: String,
    val title: String,
    val startTimeSec: Int,
    val endTimeSec: Int,
    val repeatCount: Int,
    @ElementCollection(fetch = FetchType.EAGER)
    @BatchSize(size = 10)
    @CollectionTable(
        name = "room_track_additional_title",
        joinColumns = [JoinColumn(name = "room_id"), JoinColumn(name = "track_id")]
    )
    @Column(name = "additional_title")
    val additionalTitles: Set<String>,
    @Column(name = "is_representative")
    val representative: Boolean,
    @jakarta.persistence.Id
    @ManyToOne
    @JoinColumn(name = "room_id")
    val room: Room,
    @jakarta.persistence.Id
    val trackId: Long,
)

data class RoomPlaylistTrackId(
    val room: Long = 0,
    val trackId: Long = 0,
) : Serializable
