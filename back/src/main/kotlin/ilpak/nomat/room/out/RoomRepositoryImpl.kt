package ilpak.nomat.room.out

import ilpak.nomat.room.application.domain.Room
import ilpak.nomat.room.application.domain.RoomRepository
import ilpak.nomat.room.out.jpa.RoomJpaRepository
import ilpak.nomat.room.out.jpa.entity.RoomEntity
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class RoomRepositoryImpl(
    private val roomJpaRepository: RoomJpaRepository
) : RoomRepository {

    override fun save(room: Room): Room {
        val roomEntity = RoomEntity(room)
        return roomJpaRepository.save(roomEntity).toDomain()
    }

    override fun findById(id: Long): Room? {
        return roomJpaRepository.findByIdOrNull(id)
            ?.toDomain()
    }

    override fun findAll(): List<Room> {
        return roomJpaRepository.findAll()
            .map { it.toDomain() }
    }
}
