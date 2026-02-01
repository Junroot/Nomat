package ilpak.nomat.room.application.domain

class RoomEntries(unsortedEntries: Collection<RoomEntry> = emptyList()) {
    val entries = unsortedEntries.sortedBy { it.joinDate }
    val masterEntry = entries.getOrNull(0)
    val playerIds: List<Long>
        get() = entries.map { it.playerId }
}
