package ilpak.nomat.room.application.dto

import ilpak.nomat.room.application.domain.Room

data class RoomDetailResponse(
    val id: Long,
    val title: String,
    val playlist: PlaylistDetailResponse,
    val players: List<RoomMemberResponse>
) {
    companion object {
        fun of(
            room: Room,
            trackCount: Int,
            playerIdToNicknameMap: Map<Long, String>,
        ): RoomDetailResponse {
            val playlistMasterNickname = playerIdToNicknameMap[room.playlistMasterId]
                ?: throw IllegalStateException("playlistMasterNickname not found")

            return RoomDetailResponse(
                room.id,
                room.title,
                PlaylistDetailResponse.of(room.playlist, trackCount, playlistMasterNickname),
                room.sortedEntries.mapIndexedNotNull { index, roomEntry ->
                    RoomMemberResponse(
                        playerIdToNicknameMap[roomEntry.playerId] ?: return@mapIndexedNotNull null,
                        index == 0,
                        roomEntry.playerId,
                    )
                }
            )
        }
    }
}
