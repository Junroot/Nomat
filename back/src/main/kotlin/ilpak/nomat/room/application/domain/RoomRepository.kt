package ilpak.nomat.room.application.domain

interface RoomRepository {

    fun save(room: Room): Room
    fun findById(id: Long): Room?
    fun findAll(): List<Room>
}
