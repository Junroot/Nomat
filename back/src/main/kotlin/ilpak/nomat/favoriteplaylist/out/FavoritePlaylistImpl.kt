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

    override fun findByPlayerId(playerId: Long): List<FavoritePlaylist> {
        return jpaRepository.findByIdPlayerIdAndDeletedDateNull(playerId)
    }

    override fun save(favoritePlaylist: FavoritePlaylist): FavoritePlaylist {
        return jpaRepository.save(favoritePlaylist)
    }

    override fun deleteById(id: FavoritePlaylistId) {
        return jpaRepository.deleteById(id)
    }
}

private interface FavoritePlaylistJpaRepository : CrudRepository<FavoritePlaylist, FavoritePlaylistId> {
    fun findByIdPlayerIdAndDeletedDateNull(playerId: Long): List<FavoritePlaylist>
}
