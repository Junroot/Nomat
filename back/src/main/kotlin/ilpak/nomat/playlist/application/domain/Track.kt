package ilpak.nomat.playlist.application.domain

import jakarta.persistence.*
import org.hibernate.annotations.GenericGenerator
import org.hibernate.annotations.Parameter
import org.hibernate.id.enhanced.SequenceStyleGenerator

@Entity
class Track(
	val embedId: String,
	var title: String,
	var startTimeSec: Int,
	var endTimeSec: Int,
	var repeatCount: Int,
	@ElementCollection
	@CollectionTable(name = "track_additional_title", joinColumns = [JoinColumn(name = "track_id")])
	@Column(name = "additional_title")
	var additionalTitles: Set<String>,
	@ManyToOne
	@JoinColumn(name = "playlist_id")
	val playlist: Playlist,
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "track_id_generator")
	@GenericGenerator(
		name = "track_id_generator",
		strategy = "sequence",
		parameters = [
			Parameter(name = SequenceStyleGenerator.SEQUENCE_PARAM, value = "track_sequence"),
			Parameter(name = SequenceStyleGenerator.INCREMENT_PARAM, value = "1000"),
			Parameter(name = SequenceStyleGenerator.OPT_PARAM, value = "pooled-lotl"),
		]
	)
	val id: Long = 0L,
) {

	companion object {
		const val MAX_EMBED_ID = 20
		const val MAX_TITLE_LENGTH = 100
		const val MAX_REPEAT_COUNT = 5
		const val MAX_ADDITIONAL_TITLE_COUNT = 10
	}
}
