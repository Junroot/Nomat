package ilpak.nomat.playlist.`in`

import ilpak.nomat.playlist.application.PlaylistService
import ilpak.nomat.playlist.application.dto.PlaylistCreationRequest
import ilpak.nomat.playlist.application.dto.PlaylistMetaDataResponse
import ilpak.nomat.playlist.application.dto.PlaylistResponse
import ilpak.nomat.playlist.application.dto.PlaylistWithTrackResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
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
    ): PlaylistWithTrackResponse {
        return playlistService.save(playerId, request)
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): PlaylistResponse {
        return playlistService.getById(id)
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
        )
        @Max(1000, message = "최대 요청 가능한 값은 {value} 입니다.")
        limit: Int
    ): List<PlaylistMetaDataResponse> {
        return playlistService.getRecentlyAddedPlaylists(limit)
    }

    @GetMapping(params = ["title"])
    fun searchByTitle(@RequestParam title: String): List<PlaylistMetaDataResponse> {
        return playlistService.searchByTitle(title)
    }

    @GetMapping(params = ["masterDisplayName"])
    fun getByMasterDisplayName(@RequestParam masterDisplayName: String): List<PlaylistMetaDataResponse> {
        return playlistService.getByMasterDisplayName(masterDisplayName)
    }

    @GetMapping(params = ["favoriteOf=me"])
    fun getFavoritePlaylistsOfMe(@AuthenticationPrincipal playerId: Long): List<PlaylistMetaDataResponse> {
        return playlistService.getFavoritePlaylists(playerId)
    }
}
