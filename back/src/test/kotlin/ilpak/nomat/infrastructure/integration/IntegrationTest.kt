package ilpak.nomat.infrastructure.integration

import ilpak.nomat.NomatApplication
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestExecutionListeners
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.lang.annotation.Inherited

@ExtendWith(SpringExtension::class)
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Inherited
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = [NomatApplication::class, TestConfiguration::class],
    properties = ["spring.main.allow-bean-definition-overriding=true"],
)
@AutoConfigureObservability
@TestPropertySource(properties = ["spring.profiles.active=test", "app.room.reconnect-grace-period-seconds=2"])
@EnableAutoConfiguration(exclude = [OAuth2ClientAutoConfiguration::class])
@TestExecutionListeners(
    value = [IntegrationTestExecutionListener::class],
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS
)
annotation class IntegrationTest
