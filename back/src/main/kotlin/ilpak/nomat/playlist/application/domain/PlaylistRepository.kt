package ilpak.nomat.playlist.application.domain

interface PlaylistRepository {

    fun save(playlist: Playlist): Playlist
    fun findById(id: Long): Playlist?
    fun countByMasterId(masterId: Long): Long
    fun searchByTitle(title: String, size: Int): List<Playlist>
}
