package ilpak.nomat.favoriteplaylist.application.domain

interface FavoritePlaylistRepository {

    fun findByPlayerId(playerId: Long): List<FavoritePlaylist>
    fun save(favoritePlaylist: FavoritePlaylist): FavoritePlaylist
    fun deleteById(id: FavoritePlaylistId)
}
