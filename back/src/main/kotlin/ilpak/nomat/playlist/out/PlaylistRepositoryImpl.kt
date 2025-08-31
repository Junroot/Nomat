package ilpak.nomat.playlist.out

import ilpak.nomat.playlist.application.domain.Playlist
import ilpak.nomat.playlist.application.domain.PlaylistRepository
import ilpak.nomat.playlist.out.document.PlaylistDocument
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
private class PlaylistRepositoryImpl(
    private val playlistJpaRepository: PlaylistJpaRepository,
    private val playlistDocumentRepository: PlaylistDocumentRepository,
) : PlaylistRepository {
    override fun save(playlist: Playlist): Playlist {
        return playlistJpaRepository.save(playlist)
    }

    override fun findById(id: Long): Playlist? {
        return playlistJpaRepository.findByIdOrNull(id)
    }

    override fun countByMasterId(masterId: Long): Long {
        return playlistJpaRepository.countByAuditMetadataCreatedBy(masterId)
    }

    override fun searchByTitle(title: String, size: Int): List<Playlist> {
        playlistDocumentRepository.findByTitle(title, Pageable.ofSize(size)).let { documents ->
            val ids = documents.map { it.id }
            val playlistMap = playlistJpaRepository.findAllById(ids).associateBy { it.id }
            return ids.mapNotNull { playlistMap[it] }
        }
    }
}

private interface PlaylistJpaRepository : CrudRepository<Playlist, Long> {
    fun countByAuditMetadataCreatedBy(masterId: Long): Long
}

private interface PlaylistDocumentRepository : CrudRepository<PlaylistDocument, Long> {
    fun findByTitle(title: String, pageable: Pageable): List<PlaylistDocument>
}
