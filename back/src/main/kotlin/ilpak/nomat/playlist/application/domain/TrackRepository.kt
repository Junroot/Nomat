package ilpak.nomat.playlist.application.domain

interface TrackRepository {

	fun saveAll(tracks: List<Track>): List<Track>
	fun findByPlaylist(playlist: Playlist): List<Track>
	fun countByPlaylist(playlist: Playlist): Long
}
