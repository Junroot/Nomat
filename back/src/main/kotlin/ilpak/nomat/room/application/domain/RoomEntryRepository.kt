package ilpak.nomat.room.application.domain

interface RoomEntryRepository {

    fun tryEnter(roomId: Long, playerId: Long, limit: Int): RoomEntryResult
    fun getEntries(roomId: Long): RoomEntries
    fun getEntries(roomIds: Collection<Long>): Map<Long, RoomEntries>
}
