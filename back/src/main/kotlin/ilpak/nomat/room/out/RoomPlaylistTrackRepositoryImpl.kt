package ilpak.nomat.room.out

import ilpak.nomat.room.application.domain.Room
import ilpak.nomat.room.application.domain.RoomPlaylistTrack
import ilpak.nomat.room.application.domain.RoomPlaylistTrackId
import ilpak.nomat.room.application.domain.RoomPlaylistTrackRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
private class RoomPlaylistTrackRepositoryImpl(
    private val roomPlaylistTrackJpaRepository: RoomPlaylistTrackJpaRepository
) : RoomPlaylistTrackRepository {

    override fun save(tracks: List<RoomPlaylistTrack>): List<RoomPlaylistTrack> {
        return roomPlaylistTrackJpaRepository.saveAll(tracks).toList()
    }

    override fun countByRoomId(room: Room): Long {
        return roomPlaylistTrackJpaRepository.countByRoom(room)
    }

    override fun countByRoomIds(roomIds: Collection<Long>): Map<Long, Long> {
        return roomPlaylistTrackJpaRepository.countByRoomIds(roomIds)
    }
}

private interface RoomPlaylistTrackJpaRepository : CrudRepository<RoomPlaylistTrack, RoomPlaylistTrackId> {

    fun countByRoom(room: Room): Long
    @Query(
        """
        SELECT rpt.room.id, COUNT(rpt.trackId) 
        FROM RoomPlaylistTrack rpt 
        WHERE rpt.room.id IN :roomIds 
        GROUP BY rpt.room.id
    """
    )
    fun countByRoomIds(roomIds: Collection<Long>): Map<Long, Long>
}
