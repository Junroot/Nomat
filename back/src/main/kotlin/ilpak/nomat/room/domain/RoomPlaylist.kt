package ilpak.nomat.room.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class RoomPlaylist(
    val playlistId: Long,
    @Column(name = "playlist_name")
    val name: String,
    @Column(name = "playlist_count")
    val count: Int,
    @Column(name = "playlist_master")
    val master: String,
    @Column(name = "playlist_comment")
    val comment: String,
)
