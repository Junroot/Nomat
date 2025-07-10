package ilpak.nomat.playlist.out

import ilpak.nomat.playlist.application.domain.Playlist
import ilpak.nomat.playlist.application.domain.PlaylistRepository
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
private class PlaylistRepositoryImpl(
	private val playlistJpaRepository: PlaylistJpaRepository
): PlaylistRepository {
	override fun save(playlist: Playlist): Playlist {
		return playlistJpaRepository.save(playlist)
	}

	override fun findById(id: Long): Playlist? {
		return playlistJpaRepository.findByIdOrNull(id)
	}

	override fun countByMasterId(masterId: Long): Long {
		return playlistJpaRepository.countByAuditMetadataCreatedBy(masterId)
	}
}

private interface PlaylistJpaRepository: CrudRepository<Playlist, Long> {
	fun countByAuditMetadataCreatedBy(masterId: Long): Long
}
