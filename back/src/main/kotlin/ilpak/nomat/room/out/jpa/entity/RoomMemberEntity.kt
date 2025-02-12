package ilpak.nomat.room.out.jpa.entity

import ilpak.nomat.room.application.domain.RoomMember
import jakarta.persistence.Embeddable

@Embeddable
data class RoomMemberEntity(
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
