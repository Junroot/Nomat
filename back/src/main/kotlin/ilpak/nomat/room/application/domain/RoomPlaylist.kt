package ilpak.nomat.room.application.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
class RoomPlaylist(
    @Column(name = "playlist_name")
    val name: String,
    @Column(name = "playlist_count")
    val count: Int,
    @Column(name = "playlist_master_id")
    val masterId: Long,
    @Column(name = "playlist_comment")
    val comment: String,
    @Column(name = "playlist_id")
    val id: Long,
)
