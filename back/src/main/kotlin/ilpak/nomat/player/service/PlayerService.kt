package ilpak.nomat.player.service

import ilpak.nomat.player.repository.PlayerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PlayerService(
    private val playerRepository: PlayerRepository
) {

    @Transactional(readOnly = true)
    fun exists(playerId: Long): Boolean {
        return playerRepository.existsById(playerId)
    }
}
