package ilpak.nomat.favoriteplaylist.`in`

import ilpak.nomat.favoriteplaylist.application.FavoritePlaylistService
import ilpak.nomat.playlist.application.domain.PlaylistDeleted
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

@Component
private class PlaylistDeletedHandler(
    private val favoritePlaylistService: FavoritePlaylistService,
) {

    @ApplicationModuleListener(id = "favorite-cleanup-on-playlist-deleted")
    fun handle(event: PlaylistDeleted) {
        favoritePlaylistService.deleteByPlaylistId(event.playlistId)
    }
}
