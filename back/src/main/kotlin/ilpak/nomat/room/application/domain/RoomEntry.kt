package ilpak.nomat.room.application.domain

import java.time.LocalDateTime

data class RoomEntry(
    val playerId: Long,
    val joinDate: LocalDateTime,
)
