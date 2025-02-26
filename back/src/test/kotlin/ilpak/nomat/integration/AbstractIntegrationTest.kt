package ilpak.nomat.integration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = [TestSecurityConfiguration::class]
)
@TestPropertySource(properties = ["spring.profiles.active=test"])
@EnableAutoConfiguration(exclude = [OAuth2ClientAutoConfiguration::class])
abstract class AbstractIntegrationTest {

    @LocalServerPort
    protected val port: Int = 0

    @Autowired
    protected lateinit var flyway: Flyway
    protected val client: WebTestClient by lazy {
        WebTestClient
            .bindToServer()
            .baseUrl("http://localhost:$port")
            .build()
    }


    @BeforeEach
    fun setUp() {
        flyway.clean()
        flyway.migrate()
    }
}
