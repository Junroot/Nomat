package ilpak.nomat.room.application.dto

import ilpak.nomat.room.application.domain.Room

data class RoomResponse(
    val id: Long,
    val title: String,
    val playlist: PlaylistResponse,
    val masterDisplayName: String,
    val hasPassword: Boolean,
    val maxPlayerCount: Int,
    val currentPlayerCount: Int,
    val representativeTrackEmbedId: String?,
) {

    companion object {

        fun of(
            room: Room,
            trackCount: Int,
            masterDisplayName: String,
            representativeTrackEmbedId: String?,
        ): RoomResponse {
            return RoomResponse(
                room.id,
                room.title,
                PlaylistResponse.of(room.playlist, trackCount),
                masterDisplayName,
                room.password != null,
                room.maxEntriesCount,
                room.playerIds.size,
                representativeTrackEmbedId,
            )
        }
    }
}
