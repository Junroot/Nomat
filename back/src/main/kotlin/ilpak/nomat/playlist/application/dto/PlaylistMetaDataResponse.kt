package ilpak.nomat.playlist.application.dto

import ilpak.nomat.infrastructure.exception.InternalServerErrorException
import ilpak.nomat.playlist.application.domain.Playlist

data class PlaylistMetaDataResponse(
	val id: Long,
	val name: String,
	val trackCount: Long,
	val masterId: Long,
	val comment: String
) {
	companion object {
		fun of(playlist: Playlist, trackCount: Long): PlaylistMetaDataResponse {
			return PlaylistMetaDataResponse(
				playlist.id,
				playlist.title,
				trackCount,
				playlist.auditMetadata.createdBy
					?: throw InternalServerErrorException("createdBy is null for playlist id: ${playlist.id}"),
				playlist.description
			)
		}
	}
}
