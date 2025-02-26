package ilpak.nomat.room.application.domain

import jakarta.persistence.CollectionTable
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn

@Entity
class Room(
    val title: String,
    val password: String?,
    val playlist: RoomPlaylist,
    @ElementCollection
    @CollectionTable(name = "room_entry", joinColumns = [JoinColumn(name = "room_id")])
    val entries: MutableList<RoomEntry> = mutableListOf(),
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
) {

    val master: RoomEntry?
        get() = entries.firstOrNull()

    val playerIds: Set<Long>
        get() = entries.map { it.playerId }.toSet()

    val playlistMasterId: Long
        get() = playlist.masterId

    fun isMaster(roomMember: RoomEntry): Boolean {
        return roomMember == master
    }
}
