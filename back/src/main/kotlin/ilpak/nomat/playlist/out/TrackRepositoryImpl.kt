package ilpak.nomat.playlist.out

import ilpak.nomat.playlist.application.domain.Playlist
import ilpak.nomat.playlist.application.domain.Track
import ilpak.nomat.playlist.application.domain.TrackRepository
import org.springframework.data.repository.CrudRepository
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

	override fun countByPlaylist(playlist: Playlist): Long {
		return trackJpaRepository.countByPlaylist(playlist)
	}
}

private interface TrackJpaRepository : CrudRepository<Track, Long> {
	fun findByPlaylist(playlist: Playlist): List<Track>
	fun countByPlaylist(playlist: Playlist): Long
}
