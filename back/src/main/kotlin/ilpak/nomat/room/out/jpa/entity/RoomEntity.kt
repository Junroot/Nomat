package ilpak.nomat.room.out.jpa.entity

import ilpak.nomat.room.application.domain.Room
import ilpak.nomat.room.application.domain.RoomPlaylist
import jakarta.persistence.CollectionTable
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn

@Entity(name = "room")
class RoomEntity(
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

