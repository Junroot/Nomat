package ilpak.nomat.room.dto

import ilpak.nomat.room.domain.RoomPlaylist

data class PlaylistResponse(
    val id: Long,
    val name: String,
    val count: Int,
) {
    companion object {
        fun of(playlist: RoomPlaylist): PlaylistResponse {
            return PlaylistResponse(
                playlist.playlistId,
                playlist.name,
                playlist.count
            )
        }
    }
}
