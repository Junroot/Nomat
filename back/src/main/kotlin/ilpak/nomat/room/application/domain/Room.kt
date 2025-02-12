package ilpak.nomat.room.application.domain

class Room(
    val title: String,
    val password: String?,
    val members: List<RoomMember>,
    val playlist: RoomPlaylist,
    val id: Long = 0,
) {

    val master: RoomMember?
        get() = members.firstOrNull()

    fun isMaster(roomMember: RoomMember): Boolean {
        return roomMember == master
    }
}
