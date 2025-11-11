package ilpak.nomat.favoriteplaylist.out

import ilpak.nomat.favoriteplaylist.application.domain.FavoritePlaylist
import ilpak.nomat.favoriteplaylist.application.domain.FavoritePlaylistId
import ilpak.nomat.favoriteplaylist.application.domain.FavoritePlaylistRepository
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
private class FavoritePlaylistImpl(
    private val jpaRepository: FavoritePlaylistJpaRepository,
) : FavoritePlaylistRepository {

    override fun existsById(id: FavoritePlaylistId): Boolean {
        return jpaRepository.existsById(id)
    }

    override fun findByPlayerId(playerId: Long): List<FavoritePlaylist> {
        return jpaRepository.findByIdPlayerId(playerId)
    }

    override fun save(favoritePlaylist: FavoritePlaylist): FavoritePlaylist {
        return jpaRepository.save(favoritePlaylist)
    }

    override fun deleteById(id: FavoritePlaylistId) {
        return jpaRepository.deleteById(id)
    }

    override fun deleteByPlaylistId(playlistId: Long) {
        return jpaRepository.deleteByIdPlaylistId(playlistId)
    }
}

private interface FavoritePlaylistJpaRepository : CrudRepository<FavoritePlaylist, FavoritePlaylistId> {
    fun deleteByIdPlaylistId(playlistId: Long)
    fun findByIdPlayerId(playerId: Long): List<FavoritePlaylist>
}
