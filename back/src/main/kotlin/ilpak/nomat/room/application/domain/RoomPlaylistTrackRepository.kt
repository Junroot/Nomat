package ilpak.nomat.room.application.domain

interface RoomPlaylistTrackRepository {

    fun save(tracks: List<RoomPlaylistTrack>): List<RoomPlaylistTrack>
    fun countByRoomId(room: Room): Long
    fun countByRoomIds(roomIds: Collection<Long>): Map<Long, Long>
}
