package ilpak.nomat.player.`in`

import ilpak.nomat.player.application.PlayerService
import ilpak.nomat.player.application.dto.PlayerNicknameRequest
import ilpak.nomat.player.application.dto.PlayerResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/players")
class PlayerController(
    private val playerService: PlayerService,
) {

    @GetMapping("/me")
    fun getMe(@AuthenticationPrincipal playerId: Long): PlayerResponse {
        return playerService.findById(playerId)
    }
}
