package ilpak.nomat.infrastructure.app

import ilpak.nomat.player.application.PlayerService
import ilpak.nomat.player.application.domain.RegistrationType
import ilpak.nomat.player.application.dto.PlayerRequest
import ilpak.nomat.room.application.RoomService
import ilpak.nomat.room.application.dto.RoomRequest
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Profile("local")
class AppStartupRunner(
    private val roomService: RoomService,
    private val playerService: PlayerService,
) : ApplicationRunner {

    companion object {
        private const val LOCAL_ROOM_COUNT = 40
    }

    @Transactional
    override fun run(args: ApplicationArguments?) {
        repeat(LOCAL_ROOM_COUNT) {
            val player = playerService.save(
                PlayerRequest(
                    nickname = "ROOT#3465",
                    registrationType = RegistrationType.DISCORD,
                    registrationId = "abc"
                )
            )
            roomService.createRoom(
                RoomRequest(
                    "들어오셈",
                    100,
                    null,
                    100L,
                )
            )
        }
    }
}
