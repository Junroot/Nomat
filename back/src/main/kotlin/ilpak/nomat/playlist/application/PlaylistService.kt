package ilpak.nomat.playlist.application

import ilpak.nomat.infrastructure.exception.ForbiddenException
import ilpak.nomat.infrastructure.exception.NotFoundException
import ilpak.nomat.infrastructure.exception.NotFoundResource
import ilpak.nomat.player.application.PlayerService
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
    private val playerService: PlayerService,
) {

    @Transactional
    fun save(masterId: Long, request: PlaylistCreationRequest): PlaylistResponse {
        validateToSave(masterId)

        val playlist = request.toDomain()
        val savedPlaylist = playlistRepository.save(playlist)

        val tracks = request.tracks.map { it.toDomain(savedPlaylist) }
        val savedTracks = trackRepository.saveAll(tracks)

        val master = playerService.findById(masterId)

        return PlaylistResponse.of(savedPlaylist, savedTracks, master)
    }

    private fun validateToSave(masterId: Long) {
        val countByMasterId = playlistRepository.countByMasterId(masterId)
        if (countByMasterId >= Playlist.MAX_PLAYLIST_COUNT_PER_PLAYER) {
            throw ForbiddenException("플레이어는 최대 ${Playlist.MAX_PLAYLIST_COUNT_PER_PLAYER}개의 플레이리스트를 만들 수 있습니다.")
        }
    }

    fun get(id: Long): PlaylistResponse {
        val playlist = playlistRepository.findById(id) ?: throw NotFoundException(NotFoundResource.PLAYLIST)
        val tracks = trackRepository.findByPlaylist(playlist)
        val master = playerService.findById(playlist.masterId)
        return PlaylistResponse.of(playlist, tracks, master)
    }

    fun searchByTitle(title: String): List<PlaylistMetaDataResponse> {
        val playlists = playlistRepository.searchByTitle(title, 1000)
        val masterIds = playlists.map { it.masterId }.toSet()
        val masters = playerService.findByIdIn(masterIds).associateBy { it.id }

        return playlists.mapNotNull {
            val master = masters[it.masterId] ?: return@mapNotNull null
            PlaylistMetaDataResponse.of(it, master)
        }
    }
}
