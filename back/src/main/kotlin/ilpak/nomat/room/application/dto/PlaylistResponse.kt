package ilpak.nomat.room.application.dto

import ilpak.nomat.room.application.domain.RoomPlaylist

data class PlaylistResponse(
    val title: String,
    val trackCount: Int,
    val id: Long,
) {
    companion object {
        fun of(playlist: RoomPlaylist, trackCount: Int): PlaylistResponse {
            return PlaylistResponse(
                playlist.title,
                trackCount,
                playlist.id,
            )
        }
    }
}
