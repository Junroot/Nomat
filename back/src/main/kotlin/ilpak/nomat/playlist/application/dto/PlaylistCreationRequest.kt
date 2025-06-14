package ilpak.nomat.playlist.application.dto

import ilpak.nomat.playlist.application.domain.Playlist
import ilpak.nomat.playlist.application.domain.Track
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.Length

data class PlaylistCreationRequest(
	@field:Length(min = 1, max = Playlist.MAX_TITLE_LENGTH)
	val title: String,
	@field:Length(min = 1, max = Playlist.MAX_DESCRIPTION_LENGTH)
	val description: String,
	val tracks: List<PlaylistCreationTrackRequest>,
)

data class PlaylistCreationTrackRequest(
	@field:Length(min = 1, max = Track.MAX_EMBED_ID)
	val embedId: String,
	@field:Length(min = 1, max = Track.MAX_TITLE_LENGTH)
	val title: String,
	val startTimeSec: Int,
	val endTimeSec: Int,
	@field:Min(1)
	@field:Max(Track.MAX_REPEAT_COUNT.toLong())
	val repeatCount: Int,
	@field:Size(min = 1, max = Track.MAX_ADDITIONAL_TITLE_COUNT)
	val additionalTitles: List<@Size(min = 1, max = Track.MAX_TITLE_LENGTH) String>,
)
