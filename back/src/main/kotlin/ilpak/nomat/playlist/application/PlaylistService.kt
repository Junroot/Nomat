package ilpak.nomat.playlist.application

import ilpak.nomat.favoriteplaylist.application.FavoritePlaylistService
import ilpak.nomat.common.exception.ForbiddenException
import ilpak.nomat.common.exception.NotFoundException
import ilpak.nomat.common.exception.NotFoundResource
import ilpak.nomat.player.application.PlayerService
import ilpak.nomat.player.application.dto.PlayerResponse
import ilpak.nomat.playlist.application.domain.Playlist
import ilpak.nomat.playlist.application.domain.PlaylistRepository
import ilpak.nomat.playlist.application.domain.TrackRepository
import ilpak.nomat.playlist.application.dto.PlaylistCreationRequest
import ilpak.nomat.playlist.application.dto.PlaylistMetaDataResponse
import ilpak.nomat.playlist.application.dto.PlaylistResponse
import ilpak.nomat.playlist.application.dto.PlaylistWithTrackResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PlaylistService(
    private val playlistRepository: PlaylistRepository,
    private val trackRepository: TrackRepository,
    private val playerService: PlayerService,
    private val favoritePlaylistService: FavoritePlaylistService,
) {

    @Transactional
    fun save(masterId: Long, request: PlaylistCreationRequest): PlaylistWithTrackResponse {
        validateToSave(masterId)

        val playlist = request.toDomain()
        val savedPlaylist = playlistRepository.save(playlist)

        val tracks = request.tracks.map { it.toDomain(savedPlaylist) }
        val savedTracks = trackRepository.saveAll(tracks)

        val master = playerService.findById(masterId)

        return PlaylistWithTrackResponse.of(savedPlaylist, savedTracks, master)
    }

    @Transactional
    fun update(playerId: Long, playlistId: Long, request: PlaylistCreationRequest): PlaylistWithTrackResponse {
        val playlist = playlistRepository.findById(playlistId) ?: throw NotFoundException(NotFoundResource.PLAYLIST)

        if (playlist.masterId != playerId) {
            throw ForbiddenException("본인의 플레이리스트만 수정할 수 있습니다.")
        }

        playlist.update(request.title, request.description)
        playlistRepository.save(playlist)

        trackRepository.deleteByPlaylist(playlist)
        val tracks = request.tracks.map { it.toDomain(playlist) }
        val savedTracks = trackRepository.saveAll(tracks)

        val master = playerService.findById(playerId)

        return PlaylistWithTrackResponse.of(playlist, savedTracks, master)
    }

    @Transactional
    fun delete(playerId: Long, playlistId: Long) {
        val playlist = playlistRepository.findById(playlistId) ?: throw NotFoundException(NotFoundResource.PLAYLIST)

        if (playlist.masterId != playerId) {
            throw ForbiddenException("본인의 플레이리스트만 삭제할 수 있습니다.")
        }

        playlist.markDeleted()
        trackRepository.deleteByPlaylist(playlist)
        playlistRepository.delete(playlist)
    }

    private fun validateToSave(masterId: Long) {
        val countByMasterId = playlistRepository.countByMasterId(masterId)
        if (countByMasterId >= Playlist.MAX_PLAYLIST_COUNT_PER_PLAYER) {
            throw ForbiddenException("플레이어는 최대 ${Playlist.MAX_PLAYLIST_COUNT_PER_PLAYER}개의 플레이리스트를 만들 수 있습니다.")
        }
    }

    fun getWithTracks(requestPlayerId: Long, id: Long): PlaylistWithTrackResponse {
        val playlist = playlistRepository.findById(id) ?: throw NotFoundException(NotFoundResource.PLAYLIST)

        if (playlist.masterId != requestPlayerId) {
            throw ForbiddenException("본인의 플레이리스트만 트랙과 함께 조회할 수 있습니다.")
        }

        val tracks = trackRepository.findByPlaylist(playlist)
        val master = playerService.findById(playlist.masterId)
        return PlaylistWithTrackResponse.of(playlist, tracks, master)
    }

    fun getWithTracksForInternal(id: Long): PlaylistWithTrackResponse {
        val playlist = playlistRepository.findById(id) ?: throw NotFoundException(NotFoundResource.PLAYLIST)
        val tracks = trackRepository.findByPlaylist(playlist)
        val master = playerService.findById(playlist.masterId)
        return PlaylistWithTrackResponse.of(playlist, tracks, master)
    }

    fun getById(requestRequestId: Long, id: Long): PlaylistResponse {
        val playlist = playlistRepository.findById(id) ?: throw NotFoundException(NotFoundResource.PLAYLIST)
        val tracks = trackRepository.findByPlaylist(playlist)
        val master = playerService.findById(playlist.masterId)
        val favorite = favoritePlaylistService.isFavorite(requestRequestId, playlist.id)
        return PlaylistResponse.of(playlist, tracks, master, favorite)
    }

    fun getByMasterId(masterId: Long): List<PlaylistMetaDataResponse> {
        val master = playerService.findById(masterId)
        return getPlaylistsByMaster(master)
    }

    fun searchByTitle(title: String): List<PlaylistMetaDataResponse> {
        val playlists = playlistRepository.searchByTitle(title, MAX_SEARCH_RESULT_SIZE)
        return getPlaylistMetaDataResponses(playlists)
    }

    fun getByMasterDisplayName(displayName: String): List<PlaylistMetaDataResponse> {
        val master = try {
            playerService.findByDisplayName(displayName)
        } catch (e: NotFoundException) {
            return emptyList()
        }

        return getPlaylistsByMaster(master)
    }

    private fun getPlaylistsByMaster(master: PlayerResponse): List<PlaylistMetaDataResponse> {
        val playlists = playlistRepository.findByMasterId(master.id)
        val representativeTracks = trackRepository.findByRepresentativeIsTrueAndPlaylist(playlists)
            .associateBy { it.playlist.id }

        return playlists.sortedByDescending { it.auditMetadata.createdDate }.mapNotNull {
            val representativeTrack = representativeTracks[it.id] ?: return@mapNotNull null
            PlaylistMetaDataResponse.of(it, representativeTrack, master)
        }
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

    fun getFavoritePlaylists(playerId: Long): List<PlaylistMetaDataResponse> {
        val favoritePlaylists = favoritePlaylistService.findByPlayerId(playerId)
        val playlistIds = favoritePlaylists.map { it.playlistId }.toSet()
        val playlists = playlistRepository.findAllById(playlistIds)
        return getPlaylistMetaDataResponses(playlists)
    }

    companion object {
        const val MAX_SEARCH_RESULT_SIZE = 1000
    }
}
