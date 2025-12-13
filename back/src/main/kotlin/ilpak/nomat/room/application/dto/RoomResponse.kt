package ilpak.nomat.room.application.dto

import ilpak.nomat.room.application.domain.Room

data class RoomResponse(
    val id: Long,
    val title: String,
    val playlist: PlaylistResponse,
    val masterNickname: String?,
) {

    companion object {

        fun of(room: Room, trackCount: Int, nickname: String): RoomResponse {
            return RoomResponse(
                room.id,
                room.title,
                PlaylistResponse.of(room.playlist, trackCount),
                nickname,
            )
        }
    }
}
