package ilpak.nomat.configuration

import ilpak.nomat.player.domain.Player
import ilpak.nomat.player.repository.PlayerRepository
import ilpak.nomat.room.domain.Room
import ilpak.nomat.room.domain.RoomMember
import ilpak.nomat.room.domain.RoomPlaylist
import ilpak.nomat.room.domain.RoomRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Profile("local")
class AppStartupRunner(
    private val roomRepository: RoomRepository,
    private val playerRepository: PlayerRepository,
) : ApplicationRunner {

    companion object {
        private const val LOCAL_ROOM_COUNT = 40
    }

    @Transactional
    override fun run(args: ApplicationArguments?) {
        repeat(LOCAL_ROOM_COUNT) {
            val player = playerRepository.save(Player(nickname = "ROOT#3465"))
            roomRepository.save(
                Room(
                    title = "들어오셈", null, members = listOf(RoomMember.of(player)),
                    playlist = RoomPlaylist(
                        1L,
                        "오늘의 TOP 100: 일본",
                        100
                    )
                )
            )
        }
    }
}
