package ilpak.nomat.favoriteplaylist.`in`

import ilpak.nomat.favoriteplaylist.application.FavoritePlaylistService
import ilpak.nomat.favoriteplaylist.application.dto.FavoritePlaylistRequest
import ilpak.nomat.favoriteplaylist.application.dto.FavoritePlaylistResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/favorite-playlists")
private class FavoritePlaylistController(
    private val favoritePlaylistService: FavoritePlaylistService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun save(@AuthenticationPrincipal playerId: Long, @RequestBody request: FavoritePlaylistRequest): FavoritePlaylistResponse {
        return favoritePlaylistService.save(playerId, request)
    }

    @DeleteMapping("/{playlistId}")
    fun delete(@AuthenticationPrincipal playerId: Long, @PathVariable playlistId: Long) {
        favoritePlaylistService.delete(playerId, FavoritePlaylistRequest(playlistId))
    }
}
