package ilpak.nomat.room.application.dto

import ilpak.nomat.room.application.domain.RoomPlaylist

data class PlaylistResponse(
    val name: String,
    val count: Int,
    val id: Long,
) {
    companion object {
        fun of(playlist: RoomPlaylist): PlaylistResponse {
            return PlaylistResponse(
                playlist.name,
                playlist.count,
                playlist.id,
            )
        }
    }
}
