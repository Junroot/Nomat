package ilpak.nomat.infrastructure.web

import ch.qos.logback.access.tomcat.LogbackValve
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AccessLogConfiguration(
    @Value("\${spring.profiles.active}") private val profiles: List<String>,
) {

    @Bean
    fun logbackAccessValve(): WebServerFactoryCustomizer<TomcatServletWebServerFactory> {
        return WebServerFactoryCustomizer { factory ->
            val logbackValve = LogbackValve()
            logbackValve.filename = "logback-access-${profiles[0]}.xml"
            logbackValve.isAsyncSupported = true
            factory.addContextValves(logbackValve)
        }
    }
}
