package ilpak.nomat.configuration

import ilpak.nomat.auth.NomatOAuth2UserService
import ilpak.nomat.configuration.filter.TokenAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter


@Configuration
class SecurityConfiguration(
    private val nomatOAuth2UserService: NomatOAuth2UserService,
    private val nomatAuthenticationSuccessHandler: NomatAuthenticationSuccessHandler,
    private val tokenAuthenticationFilter: TokenAuthenticationFilter,
) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        return http.authorizeHttpRequests {
            it.requestMatchers(*permittedUrls.toTypedArray()).permitAll()
                .anyRequest().authenticated()
        }.oauth2Login { oauth2Login ->
            oauth2Login.authorizationEndpoint { }
                .userInfoEndpoint { userInfoEndpoint -> userInfoEndpoint.userService(nomatOAuth2UserService) }
                .successHandler(nomatAuthenticationSuccessHandler)
        }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling { it.authenticationEntryPoint(Http403ForbiddenEntryPoint()) }
            .addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
    }

    companion object {
        private val permittedUrls = setOf("/login/**", "/html/**", "/health/**")
    }
}
