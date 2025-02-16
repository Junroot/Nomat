package ilpak.nomat.room.application.domain

import jakarta.persistence.Embeddable

@Embeddable
data class RoomEntry(
    val playerId: Long,
)
