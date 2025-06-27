package ilpak.nomat.playlist.application.domain

import jakarta.persistence.*

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
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	val id: Long = 0L,
) {

	companion object {
		const val MAX_EMBED_ID = 20
		const val MAX_TITLE_LENGTH = 100
		const val MAX_REPEAT_COUNT = 5
		const val MAX_ADDITIONAL_TITLE_COUNT = 10
	}
}
