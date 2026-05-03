package ilpak.nomat.playlist.application.domain

data class PlaylistUpserted(
    val id: Long,
    val masterId: Long,
    val title: String,
    val description: String,
) {
    companion object {
        fun from(playlist: Playlist): PlaylistUpserted = PlaylistUpserted(
            id = playlist.id,
            masterId = playlist.masterId,
            title = playlist.title,
            description = playlist.description,
        )
    }
}
