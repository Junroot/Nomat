package ilpak.nomat.room.application.dto

import ilpak.nomat.room.application.domain.RoomPlaylist

data class PlaylistDetailResponse(
    val id: Long,
    val title: String,
    val count: Int,
    val master: String,
    val description: String,
) {
    companion object {
        fun of(playlist: RoomPlaylist, trackCount: Int, masterNickname: String): PlaylistDetailResponse {
            return PlaylistDetailResponse(
                playlist.id,
                playlist.title,
                trackCount,
                masterNickname,
                playlist.description
            )
        }
    }
}
