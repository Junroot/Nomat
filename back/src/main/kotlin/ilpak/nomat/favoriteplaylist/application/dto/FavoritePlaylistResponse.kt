package ilpak.nomat.favoriteplaylist.application.dto

import ilpak.nomat.favoriteplaylist.application.domain.FavoritePlaylist

data class FavoritePlaylistResponse(
    val playerId: Long,
    val playlistId: Long,
) {
    companion object {
        fun from(domain: FavoritePlaylist): FavoritePlaylistResponse {
            return FavoritePlaylistResponse(
                playerId = domain.playerId,
                playlistId = domain.playlistId,
            )
        }
    }
}
