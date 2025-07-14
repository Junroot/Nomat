package ilpak.nomat.playlist.application.dto

import ilpak.nomat.infrastructure.exception.InternalServerErrorException
import ilpak.nomat.playlist.application.domain.Playlist
import ilpak.nomat.playlist.application.domain.Track

data class PlaylistResponse(
	val id: Long,
	val title: String,
	val description: String,
	val masterId: Long,
	val tracks: List<PlaylistTrackResponse>
) {
	companion object {
		fun of(playlist: Playlist, tracks: List<Track>): PlaylistResponse {
			return PlaylistResponse(
				playlist.id,
				playlist.title,
				playlist.description,
				playlist.auditMetadata.createdBy
					?: throw InternalServerErrorException("createdBy is null for playlist id: ${playlist.id}"),
				tracks.map { PlaylistTrackResponse.of(it) }
			)
		}
	}
}

data class PlaylistTrackResponse(
	val embedId: String,
	val title: String,
	val startTimeSec: Int,
	val endTimeSec: Int,
	val repeatCount: Int,
	val additionalTitles: Set<String>,
	val isRepresentative: Boolean,
) {
	companion object {
		fun of(track: Track): PlaylistTrackResponse {
			return PlaylistTrackResponse(
				track.embedId,
				track.title,
				track.startTimeSec,
				track.endTimeSec,
				track.repeatCount,
				track.additionalTitles,
				track.isRepresentative,
			)
		}
	}
}
