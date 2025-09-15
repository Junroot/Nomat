package ilpak.nomat.playlist.`in`

import ilpak.nomat.playlist.application.PlaylistService
import ilpak.nomat.playlist.application.dto.PlaylistCreationRequest
import ilpak.nomat.playlist.application.dto.PlaylistMetaDataResponse
import ilpak.nomat.playlist.application.dto.PlaylistResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/playlists")
private class PlaylistController(
    private val playlistService: PlaylistService
) {

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    fun save(
        @AuthenticationPrincipal playerId: Long,
        @Valid @RequestBody request: PlaylistCreationRequest
    ): PlaylistResponse {
        return playlistService.save(playerId, request)
    }

    @GetMapping(params = ["masterId=me"])
    fun getMyPlaylists(@AuthenticationPrincipal playerId: Long): List<PlaylistMetaDataResponse> {
        return playlistService.getByMasterId(playerId)
    }

    @GetMapping(params = ["sort=createdAt,desc", "limit"])
    fun getRecentlyAddedPlaylists(
        @RequestParam(
            required = false,
            defaultValue = "1000"
        ) limit: Int
    ): List<PlaylistMetaDataResponse> {
        return playlistService.getRecentlyAddedPlaylists(limit)
    }

    @GetMapping
    fun searchByTitle(@RequestParam title: String): List<PlaylistMetaDataResponse> {
        return playlistService.searchByTitle(title)
    }
}
