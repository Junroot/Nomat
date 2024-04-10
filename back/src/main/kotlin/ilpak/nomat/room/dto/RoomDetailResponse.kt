package ilpak.nomat.room.dto

import ilpak.nomat.room.domain.Room

class RoomDetailResponse(
    val id: Long,
    val title: String,
    val playlist: PlaylistDetailResponse,
    val players: List<RoomMemberResponse>
) {
    companion object {
        fun of(room: Room): RoomDetailResponse {
            return RoomDetailResponse(
                room.id,
                room.title,
                PlaylistDetailResponse.of(room.playlist),
                room.members.map {
                    RoomMemberResponse(
                        it.nickname,
                        "",
                        room.isMaster(it)
                    )
                }
            )
        }
    }
}
