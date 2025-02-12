package ilpak.nomat.room.out

import ilpak.nomat.room.application.domain.Room
import ilpak.nomat.room.application.domain.RoomMember
import ilpak.nomat.room.application.domain.RoomPlaylist
import ilpak.nomat.room.application.domain.RoomRepository
import jakarta.persistence.CollectionTable
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
private class RoomRepositoryImpl(
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

private interface RoomJpaRepository : CrudRepository<RoomEntity, Long>

@Entity(name = "room")
private class RoomEntity(
    val title: String,
    val password: String?,
    @ElementCollection
    @CollectionTable(name = "room_member", joinColumns = [JoinColumn(name = "room_id")])
    val members: List<RoomMemberEntity>,
    val playlistId: Long,
    val playlistName: String,
    val playlistCount: Int,
    val playlistMaster: String,
    val playlistComment: String,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
) {

    constructor(room: Room) : this(
        room.title,
        room.password,
        room.members.map { RoomMemberEntity(it) },
        room.playlist.id,
        room.playlist.name,
        room.playlist.count,
        room.playlist.master,
        room.playlist.comment,
        room.id,
    )

    fun toDomain(): Room {
        return Room(
            title,
            password,
            members.map { it.toDomain() },
            RoomPlaylist(playlistName, playlistCount, playlistMaster, playlistComment, playlistId),
            id
        )
    }
}

@Embeddable
private data class RoomMemberEntity(
    val playerId: Long,
    val nickname: String,
) {
    constructor(roomMember: RoomMember) : this(
        roomMember.playerId,
        roomMember.nickname,
    )

    fun toDomain(): RoomMember {
        return RoomMember(
            playerId,
            nickname,
        )
    }
}
