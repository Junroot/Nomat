package ilpak.nomat.playlist.application

import ilpak.nomat.infrastructure.exception.ForbiddenException
import ilpak.nomat.infrastructure.exception.NotFoundException
import ilpak.nomat.infrastructure.exception.NotFoundResource
import ilpak.nomat.playlist.application.domain.Playlist
import ilpak.nomat.playlist.application.domain.PlaylistRepository
import ilpak.nomat.playlist.application.domain.TrackRepository
import ilpak.nomat.playlist.application.dto.PlaylistCreationRequest
import ilpak.nomat.playlist.application.dto.PlaylistMetaDataResponse
import ilpak.nomat.playlist.application.dto.PlaylistResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PlaylistService(
	private val playlistRepository: PlaylistRepository,
	private val trackRepository: TrackRepository,
) {

	@Transactional
	fun save(masterId: Long, request: PlaylistCreationRequest): PlaylistResponse {
		validateToSave(masterId)

		val playlist = request.toDomain(masterId)
		val savedPlaylist = playlistRepository.save(playlist)

		val tracks = request.tracks.map { it.toDomain(savedPlaylist) }
		val savedTracks = trackRepository.saveAll(tracks)

		return PlaylistResponse.of(savedPlaylist, savedTracks)
	}

	private fun validateToSave(masterId: Long) {
		val countByMasterId = playlistRepository.countByMasterId(masterId)
		if (countByMasterId >= Playlist.MAX_PLAYLIST_COUNT_PER_PLAYER) {
			throw ForbiddenException("player cannot create more than ${Playlist.MAX_PLAYLIST_COUNT_PER_PLAYER} playlists.")
		}
	}

	fun getPlaylistMetadata(id: Long): PlaylistMetaDataResponse {
		val playlist = playlistRepository.findById(id) ?: throw NotFoundException(NotFoundResource.PLAYLIST)
		val trackCount = trackRepository.countByPlaylist(playlist)
		return PlaylistMetaDataResponse.of(playlist, trackCount)
	}
}
