package ilpak.nomat.playlist.application.domain

interface PlaylistRepository {

    fun save(playlist: Playlist): Playlist
    fun delete(playlist: Playlist)
    fun findById(id: Long): Playlist?
    fun findAllById(ids: Collection<Long>): List<Playlist>
    fun findByMasterId(masterId: Long): List<Playlist>
    fun findRecentlyAdded(limit: Int): List<Playlist>
    fun countByMasterId(masterId: Long): Long
    fun searchByTitle(title: String, size: Int): List<Playlist>
}
