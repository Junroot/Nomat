package ilpak.nomat.infrastructure.jpa

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@Configuration
@EnableJpaAuditing
class JpaConfiguration {

	@Bean
	fun auditorProvider(): AuditorAwareImpl {
		return AuditorAwareImpl()
	}
}
