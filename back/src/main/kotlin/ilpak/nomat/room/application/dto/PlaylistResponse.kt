package ilpak.nomat.room.application.dto

import ilpak.nomat.room.application.domain.RoomPlaylist

data class PlaylistResponse(
    val name: String,
    val count: Long,
    val id: Long,
) {
    companion object {
        fun of(playlist: RoomPlaylist): PlaylistResponse {
            return PlaylistResponse(
                playlist.name,
                playlist.trackCount,
                playlist.id,
            )
        }
    }
}
