package ilpak.nomat.playlist.application.domain

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.hibernate.annotations.GenericGenerator
import org.hibernate.annotations.Parameter
import org.hibernate.id.enhanced.SequenceStyleGenerator

@Entity
class Playlist(
	var title: String,
	val masterId: Long,
	var description: String,
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "playlist_id_generator")
	@GenericGenerator(
		name = "playlist_id_generator",
		strategy = "sequence",
		parameters = [
			Parameter(name = SequenceStyleGenerator.SEQUENCE_PARAM, value = "playlist_sequence"),
			Parameter(name = SequenceStyleGenerator.INCREMENT_PARAM, value = "1000"),
			Parameter(name = SequenceStyleGenerator.OPT_PARAM, value = "pooled-lotl"),
		]
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
