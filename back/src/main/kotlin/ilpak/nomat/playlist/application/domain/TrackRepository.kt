package ilpak.nomat.playlist.application.domain

interface TrackRepository {

    fun saveAll(tracks: List<Track>): List<Track>
    fun findByPlaylist(playlist: Playlist): List<Track>
    fun findByRepresentativeIsTrueAndPlaylist(playlists: Collection<Playlist>): List<Track>
    fun countByPlaylist(playlist: Playlist): Long
    fun countByPlaylists(playlists: Collection<Playlist>): Map<Long, Long>
    fun deleteByPlaylist(playlist: Playlist)
}
