package ilpak.nomat.room.application.domain

import jakarta.persistence.Embeddable
import java.time.LocalDateTime

@Embeddable
data class RoomEntry(
    val playerId: Long,
    val joinDate: LocalDateTime = LocalDateTime.now(),
)
