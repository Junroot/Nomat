package ilpak.nomat.room.application.dto

import ilpak.nomat.room.application.domain.RoomPlaylist

data class PlaylistDetailResponse(
    val id: Long,
    val name: String,
    val count: Long,
    val master: String,
    val comment: String,
) {
    companion object {
        fun of(playlist: RoomPlaylist, masterNickname: String): PlaylistDetailResponse {
            return PlaylistDetailResponse(
                playlist.id,
                playlist.name,
                playlist.trackCount,
                masterNickname,
                playlist.comment
            )
        }
    }
}
