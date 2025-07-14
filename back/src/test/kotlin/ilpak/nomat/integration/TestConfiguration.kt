package ilpak.nomat.integration

import ilpak.nomat.infrastructure.security.SecurityConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Lazy
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.filter.OncePerRequestFilter

@TestConfiguration
@ComponentScan(basePackages = ["ilpak.nomat.integration"], lazyInit = true)
@EnableWebSecurity
@Lazy
class TestConfiguration {

	@Bean
	fun authenticationFilter(): OncePerRequestFilter {
		return TestAuthenticationFilter()
	}

	@Bean
	fun filterChain(http: HttpSecurity): SecurityFilterChain {
		return http.authorizeHttpRequests {
			it.requestMatchers(*SecurityConfiguration.permittedUrls.toTypedArray()).permitAll()
				.anyRequest().authenticated()
		}.formLogin { it.disable() }
			.httpBasic { it.disable() }
			.csrf { it.disable() }
			.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
			.exceptionHandling { it.authenticationEntryPoint(Http403ForbiddenEntryPoint()) }
			.addFilterBefore(authenticationFilter(), UsernamePasswordAuthenticationFilter::class.java)
			.build()
	}

	@Bean
	fun webTestClient(@LocalServerPort port: Int): WebTestClient {
		return WebTestClient
			.bindToServer()
			.baseUrl("http://localhost:$port")
			.build()
	}
}
