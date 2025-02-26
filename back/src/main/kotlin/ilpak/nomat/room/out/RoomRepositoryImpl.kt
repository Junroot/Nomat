package ilpak.nomat.room.out

import ilpak.nomat.room.application.domain.Room
import ilpak.nomat.room.application.domain.RoomRepository
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
private class RoomRepositoryImpl(
    private val roomJpaRepository: RoomJpaRepository
) : RoomRepository {

    override fun save(room: Room): Room {
        return roomJpaRepository.save(room)
    }

    override fun findById(id: Long): Room? {
        return roomJpaRepository.findByIdOrNull(id)
    }

    override fun findAll(): List<Room> {
        return roomJpaRepository.findAll().toList()
    }
}

private interface RoomJpaRepository : CrudRepository<Room, Long>

