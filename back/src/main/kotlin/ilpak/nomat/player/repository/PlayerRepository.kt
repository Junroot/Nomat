package ilpak.nomat.player.repository

import ilpak.nomat.player.domain.Player
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface PlayerRepository : CrudRepository<Player, Long>
