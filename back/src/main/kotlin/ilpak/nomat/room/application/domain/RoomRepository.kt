package ilpak.nomat.room.application.domain

interface RoomRepository {

    fun save(room: Room): Room
    fun delete(room: Room)
    fun findById(id: Long): Room?
    fun findPlayerIdsByRoomId(roomId: Long): Set<Long>
    fun findByIdGreaterThanAndStatusOrderByIdDesc(id: Long, status: RoomStatus, size: Int): List<Room>
}
