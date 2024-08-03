package ilpak.nomat.configuration

import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.testcontainers.containers.MySQLContainer

@Configuration(proxyBeanMethods = false)
@Profile(value = ["local", "test"])
class ContainerConfiguration {

    @Bean
    @ServiceConnection
    fun mariaDbContainer(): MySQLContainer<*> {
        return MySQLContainer("mysql:8.0.39")
    }
}
