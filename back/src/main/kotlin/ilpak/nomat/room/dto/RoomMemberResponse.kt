package ilpak.nomat.room.dto

import ilpak.nomat.room.domain.RoomMember

data class RoomMemberResponse(
    val nickname: String,
    val photoUrl: String,
    val isMaster: Boolean
)
