package ilpak.nomat.playlist.application.domain

import jakarta.persistence.*

@Entity
class Playlist(
	var title: String,
	val masterId: Long,
	var description: String,
	@Id
	@GeneratedValue(strategy = GenerationType.TABLE, generator = "playlist_id_generator")
	@TableGenerator(
		name = "playlist_id_generator",
		table = "hibernate_sequences",
		pkColumnName = "sequence_name",
		pkColumnValue = "playlist",
		allocationSize = 1000,
	)
	val id: Long = 0L,
) {

	companion object {
		const val MAX_TITLE_LENGTH = 100
		const val MAX_DESCRIPTION_LENGTH = 500
		const val MAX_TRACK_COUNT = 1000
		const val MAX_PLAYLIST_COUNT_PER_PLAYER = 100
	}
}
