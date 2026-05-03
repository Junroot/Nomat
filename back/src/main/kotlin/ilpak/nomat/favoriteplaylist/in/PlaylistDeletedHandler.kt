package ilpak.nomat.favoriteplaylist.`in`

import ilpak.nomat.favoriteplaylist.application.domain.FavoritePlaylistRepository
import ilpak.nomat.playlist.application.domain.PlaylistDeleted
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

@Component
private class PlaylistDeletedHandler(
    private val favoritePlaylistRepository: FavoritePlaylistRepository,
) {

    @ApplicationModuleListener(id = "favorite-cleanup-on-playlist-deleted")
    fun handle(event: PlaylistDeleted) {
        favoritePlaylistRepository.deleteByPlaylistId(event.playlistId)
    }
}
