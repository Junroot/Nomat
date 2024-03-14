package ilpak.nomat.room.domain

import jakarta.persistence.*

@Entity
class Room(
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	val id: Long = 0,
	val title: String,
	val password: String?,
	@ElementCollection
	@CollectionTable(name = "room_member", joinColumns = [JoinColumn(name = "room_id")])
	val members: List<RoomMember>,
	@Embedded
	val playlist: RoomPlaylist,
) {

	constructor(title: String, password: String?, members: List<RoomMember>, playlist: RoomPlaylist) : this(0, title, password, members, playlist)

	val master: RoomMember?
		get() = members.firstOrNull()
}
