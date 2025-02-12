package ilpak.nomat.room.out.jpa

import ilpak.nomat.room.out.jpa.entity.RoomEntity
import org.springframework.data.repository.CrudRepository

interface RoomJpaRepository : CrudRepository<RoomEntity, Long>
