package ilpak.nomat.room.application.dto

import ilpak.nomat.room.application.domain.Room

class RoomDetailResponse(
    val id: Long,
    val title: String,
    val playlist: PlaylistDetailResponse,
    val players: List<RoomMemberResponse>
) {
    companion object {
        fun of(room: Room, nicknameByPlayerId: Map<Long, String>): RoomDetailResponse {
            val playlistMasterNickname = nicknameByPlayerId[room.playlistMasterId]
                ?: throw IllegalStateException("playlistMasterNickname not found")

            return RoomDetailResponse(
                room.id,
                room.title,
                PlaylistDetailResponse.of(room.playlist, playlistMasterNickname),
                room.entries.mapNotNull {
                    RoomMemberResponse(
                        nicknameByPlayerId[it.playerId] ?: return@mapNotNull null,
                        "",
                        room.isMaster(it),
                    )
                }
            )
        }
    }
}
