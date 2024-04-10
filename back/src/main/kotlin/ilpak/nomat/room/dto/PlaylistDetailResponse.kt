package ilpak.nomat.room.dto

import ilpak.nomat.room.domain.RoomPlaylist

data class PlaylistDetailResponse(
    val id: Long,
    val name: String,
    val count: Int,
    val master: String,
    val comment: String,
) {
    companion object {
        fun of(playlist: RoomPlaylist): PlaylistDetailResponse {
            return PlaylistDetailResponse(
                playlist.playlistId,
                playlist.name,
                playlist.count,
                playlist.master,
                playlist.comment
            )
        }
    }
}
