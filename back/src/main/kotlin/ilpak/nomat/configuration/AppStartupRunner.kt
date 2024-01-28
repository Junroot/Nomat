package ilpak.nomat.configuration

import ilpak.nomat.room.domain.Room
import ilpak.nomat.room.domain.RoomRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("local")
class AppStartupRunner(
	private val roomRepository: RoomRepository,
): ApplicationRunner {

	override fun run(args: ApplicationArguments?) {
		roomRepository.deleteAll()
		roomRepository.saveAll((1..40).map{ Room(playlistId = 1L, title = "들어오셈", masterId = 1L) })
	}
}
