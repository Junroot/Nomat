package ilpak.nomat.room.application.domain

import jakarta.persistence.*

@Entity
class Room(
	val title: String,
	val password: String?,
	val playlist: RoomPlaylist,
	@ElementCollection
	@CollectionTable(name = "room_entry", joinColumns = [JoinColumn(name = "room_id")])
	val entries: MutableList<RoomEntry> = mutableListOf(),
	@Id
	@GeneratedValue(strategy = GenerationType.TABLE, generator = "room_id_generator")
	@TableGenerator(
		name = "room_id_generator",
		table = "hibernate_sequences",
		pkColumnName = "sequence_name",
		pkColumnValue = "room",
		allocationSize = 1000,
	)
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
