package ilpak.nomat.favoriteplaylist.application

import ilpak.nomat.favoriteplaylist.application.domain.FavoritePlaylist
import ilpak.nomat.favoriteplaylist.application.domain.FavoritePlaylistId
import ilpak.nomat.favoriteplaylist.application.domain.FavoritePlaylistRepository
import ilpak.nomat.favoriteplaylist.application.dto.FavoritePlaylistRequest
import ilpak.nomat.favoriteplaylist.application.dto.FavoritePlaylistResponse
import ilpak.nomat.infrastructure.exception.ForbiddenException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class FavoritePlaylistService(
    private val favoritePlaylistRepository: FavoritePlaylistRepository,
) {
    fun findByPlayerId(playerId: Long): List<FavoritePlaylistResponse> {
        return favoritePlaylistRepository.findByPlayerId(playerId).map { FavoritePlaylistResponse.from(it) }
    }

    @Transactional
    fun save(playerId: Long, request: FavoritePlaylistRequest): FavoritePlaylistResponse {
        val playlists = findByPlayerId(playerId)
        if (playlists.size >= FavoritePlaylist.MAX_FAVORITE_PLAYLISTS) {
            throw ForbiddenException("즐겨찾기 플레이리스트는 최대 ${FavoritePlaylist.MAX_FAVORITE_PLAYLISTS}개까지 등록할 수 있습니다.")
        }

        val id = FavoritePlaylistId(
            playerId = playerId,
            playlistId = request.playlistId
        )

        return FavoritePlaylistResponse.from(favoritePlaylistRepository.save(FavoritePlaylist(id)))
    }

    @Transactional
    fun delete(playerId: Long, request: FavoritePlaylistRequest) {
        favoritePlaylistRepository.deleteById(
            id = FavoritePlaylistId(
                playerId = playerId,
                playlistId = request.playlistId
            )
        )
    }

    @Transactional
    fun deleteByPlaylistId(playlistId: Long) {
        favoritePlaylistRepository.deleteByPlaylistId(playlistId)
    }

    fun isFavorite(userId: Long, playlistId: Long): Boolean {
        return favoritePlaylistRepository.existsById(
            FavoritePlaylistId(
                playerId = userId,
                playlistId = playlistId
            )
        )
    }
}
