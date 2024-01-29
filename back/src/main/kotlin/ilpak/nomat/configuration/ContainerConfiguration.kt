package ilpak.nomat.configuration

import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.testcontainers.containers.MariaDBContainer

@Configuration(proxyBeanMethods = false)
@Profile("local")
class ContainerConfiguration {

	@Bean
	@ServiceConnection
	fun mariaDbContainer(): MariaDBContainer<*> {
		return MariaDBContainer("mariadb:10.11.6").withReuse(true)
	}
}
