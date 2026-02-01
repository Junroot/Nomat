package ilpak.nomat.room.application.dto

import ilpak.nomat.room.application.domain.Room
import ilpak.nomat.room.application.domain.RoomEntries

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
            entries: RoomEntries,
            playerIdToNicknameMap: Map<Long, String>,
        ): RoomDetailResponse {
            val playlistMasterNickname = playerIdToNicknameMap[room.playlistMasterId]
                ?: throw IllegalStateException("playlistMasterNickname not found")

            return RoomDetailResponse(
                room.id,
                room.title,
                PlaylistDetailResponse.of(room.playlist, trackCount, playlistMasterNickname),
                entries.entries.mapNotNull { entry ->
                    RoomMemberResponse(
                        playerIdToNicknameMap[entry.playerId] ?: return@mapNotNull null,
                        entry.playerId == entries.masterEntry?.playerId,
                        entry.playerId,
                    )
                }
            )
        }
    }
}
