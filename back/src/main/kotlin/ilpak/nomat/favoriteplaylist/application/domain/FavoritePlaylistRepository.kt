package ilpak.nomat.favoriteplaylist.application.domain

interface FavoritePlaylistRepository {

    fun existsById(id: FavoritePlaylistId): Boolean
    fun findByPlayerId(playerId: Long): List<FavoritePlaylist>
    fun findDeletedById(id: FavoritePlaylistId): FavoritePlaylist?
    fun save(favoritePlaylist: FavoritePlaylist): FavoritePlaylist
    fun deleteById(id: FavoritePlaylistId)
}
