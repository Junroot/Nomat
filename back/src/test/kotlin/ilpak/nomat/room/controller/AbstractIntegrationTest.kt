package ilpak.nomat.room.controller

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractIntegrationTest {

	@LocalServerPort
	protected val port: Int = 0
	@Autowired
	protected lateinit var flyway: Flyway
	private var webTestClient: WebTestClient? = null
	protected val client: WebTestClient
		get() {
			return webTestClient ?: synchronized(this) {
				webTestClient ?: WebTestClient
					.bindToServer()
					.baseUrl("http://localhost:$port")
					.build()
			}
		}

	@BeforeEach
	fun setUp() {
		flyway.clean()
		flyway.migrate()
	}
}
