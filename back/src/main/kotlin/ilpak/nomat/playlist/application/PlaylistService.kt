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

    fun getByMasterId(masterId: Long): List<PlaylistMetaDataResponse> {
        val master = playerService.findById(masterId)
        val playlists = playlistRepository.findByMasterId(masterId)
        val representativeTracks = trackRepository.findByRepresentativeIsTrueAndPlaylist(playlists)
            .associateBy { it.playlist.id }

        return playlists.mapNotNull {
            val representativeTrack = representativeTracks[it.id] ?: return@mapNotNull null
            PlaylistMetaDataResponse.of(it, representativeTrack, master)
        }
    }

    fun searchByTitle(title: String): List<PlaylistMetaDataResponse> {
        val playlists = playlistRepository.searchByTitle(title, MAX_SEARCH_RESULT_SIZE)
        return getPlaylistMetaDataResponses(playlists)
    }

    fun getRecentlyAddedPlaylists(size: Int): List<PlaylistMetaDataResponse> {
        val playlists = playlistRepository.findRecentlyAdded(size)
        return getPlaylistMetaDataResponses(playlists)
    }

    private fun getPlaylistMetaDataResponses(playlists: List<Playlist>): List<PlaylistMetaDataResponse> {
        val masterIds = playlists.map { it.masterId }.toSet()
        val masters = playerService.findByIdIn(masterIds).associateBy { it.id }
        val representativeTracks = trackRepository.findByRepresentativeIsTrueAndPlaylist(playlists)
            .associateBy { it.playlist.id }

        return playlists.mapNotNull {
            val master = masters[it.masterId] ?: return@mapNotNull null
            val representativeTrack = representativeTracks[it.id] ?: return@mapNotNull null
            PlaylistMetaDataResponse.of(it, representativeTrack, master)
        }
    }

    companion object {
        const val MAX_SEARCH_RESULT_SIZE = 1000
    }
}
