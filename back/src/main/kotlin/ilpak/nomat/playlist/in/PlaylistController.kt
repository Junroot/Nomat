package ilpak.nomat.playlist.`in`

import ilpak.nomat.playlist.application.PlaylistService
import ilpak.nomat.playlist.application.dto.PlaylistCreationRequest
import ilpak.nomat.playlist.application.dto.PlaylistResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/playlists")
private class PlaylistController(
	private val playlistService: PlaylistService
) {

	@ResponseStatus(HttpStatus.CREATED)
	@PostMapping
	fun save(@AuthenticationPrincipal playerId: Long, @Valid @RequestBody request: PlaylistCreationRequest): PlaylistResponse {
		return playlistService.save(playerId, request)
	}
}
