package ilpak.nomat.playlist.application.domain

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id

@Entity
class Playlist(
	var title: String,
	val masterId: Long,
	var description: String,
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	val id: Long = 0L,
) {

	companion object {
		const val MAX_TITLE_LENGTH = 100
		const val MAX_DESCRIPTION_LENGTH = 500
		const val MAX_TRACK_COUNT = 1000
		const val MAX_PLAYLIST_COUNT_PER_PLAYER = 100
	}
}
