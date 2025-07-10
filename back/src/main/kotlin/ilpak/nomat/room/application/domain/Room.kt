package ilpak.nomat.room.application.domain

import ilpak.nomat.common.AuditMetadata
import jakarta.persistence.*
import org.springframework.data.jpa.domain.support.AuditingEntityListener

@Entity
@EntityListeners(AuditingEntityListener::class)
class Room(
	val title: String,
	val password: String?,
	val playlist: RoomPlaylist,
	@ElementCollection
	@CollectionTable(name = "room_entry", joinColumns = [JoinColumn(name = "room_id")])
	val entries: MutableList<RoomEntry> = mutableListOf(),
	@Embedded
	val auditMetadata: AuditMetadata = AuditMetadata(),
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
