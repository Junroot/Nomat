package ilpak.nomat.playlist.application.dto

import ilpak.nomat.infrastructure.exception.InvalidParameterException
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
	@field:Size(min = 1, max = Playlist.MAX_TRACK_COUNT)
	val tracks: List<PlaylistCreationRequestTrack>,
) {

	fun toDomain(masterId: Long): Playlist {
		return Playlist(
			title = title,
			masterId = masterId,
			description = description,
		)
	}
}

data class PlaylistCreationRequestTrack(
	@field:Length(min = 1, max = Track.MAX_EMBED_ID)
	val embedId: String,
	@field:Length(min = 1, max = Track.MAX_TITLE_LENGTH)
	val title: String,
	@field:Min(0)
	val startTimeSec: Int,
	@field:Min(0)
	val endTimeSec: Int,
	@field:Min(1)
	@field:Max(Track.MAX_REPEAT_COUNT.toLong())
	val repeatCount: Int,
	@field:Size(min = 1, max = Track.MAX_ADDITIONAL_TITLE_COUNT)
	val additionalTitles: Set<@Length(min = 1, max = Track.MAX_TITLE_LENGTH) String>,
) {
	init {
		if (startTimeSec > endTimeSec) {
			throw InvalidParameterException("startTimeSec must be less than or equal to endTimeSec")
		}
	}

	fun toDomain(playlist: Playlist): Track {
		return Track(
			embedId = embedId,
			title = title,
			startTimeSec = startTimeSec,
			endTimeSec = endTimeSec,
			repeatCount = repeatCount,
			additionalTitles = additionalTitles,
			playlist = playlist,
		)
	}
}
