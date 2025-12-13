package ilpak.nomat.room.application.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
class RoomPlaylist(
    @Column(name = "playlist_title")
    val title: String,
    @Column(name = "playlist_master_id")
    val masterId: Long,
    @Column(name = "playlist_description")
    val description: String,
    @Column(name = "playlist_id")
    val id: Long,
)
