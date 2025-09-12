package ilpak.nomat.playlist.out

import ilpak.nomat.playlist.application.domain.Playlist
import ilpak.nomat.playlist.application.domain.Track
import ilpak.nomat.playlist.application.domain.TrackRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
private class TrackRepositoryImpl(
    private val trackJpaRepository: TrackJpaRepository,
) : TrackRepository {

    override fun saveAll(tracks: List<Track>): List<Track> {
        return trackJpaRepository.saveAll(tracks).toList()
    }

    override fun findByPlaylist(playlist: Playlist): List<Track> {
        return trackJpaRepository.findByPlaylist(playlist)
    }

    override fun findByRepresentativeIsTrueAndPlaylist(playlists: Collection<Playlist>): List<Track> {
        return trackJpaRepository.findByRepresentativeAndPlaylistIdIn(true, playlists.map { it.id })
    }

    override fun countByPlaylist(playlist: Playlist): Long {
        return trackJpaRepository.countByPlaylist(playlist)
    }

    override fun countByPlaylists(playlists: Collection<Playlist>): Map<Long, Long> {
        return trackJpaRepository.countByPlaylists(playlists)
    }
}

private interface TrackJpaRepository : CrudRepository<Track, Long> {
    fun findByPlaylist(playlist: Playlist): List<Track>
    fun findByRepresentativeAndPlaylistIdIn(representative: Boolean, playlistIds: Collection<Long>): List<Track>
    fun countByPlaylist(playlist: Playlist): Long

    @Query(
        """
        SELECT t.playlist.id, COUNT(t.id) 
        FROM Track t 
        WHERE t.playlist IN :playlists 
        GROUP BY t.playlist.id
    """
    )
    fun countByPlaylists(@Param("playlists") playlists: Collection<Playlist>): Map<Long, Long>
}
