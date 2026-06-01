package ilpak.nomat.infrastructure.security

import ilpak.nomat.auth.application.NomatOAuth2UserService
import ilpak.nomat.infrastructure.web.filter.MDCLoggingFilter
import ilpak.nomat.infrastructure.web.filter.TokenAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@Profile("!test")
class SecurityConfiguration(
    private val nomatOAuth2UserService: NomatOAuth2UserService,
    private val nomatAuthenticationSuccessHandler: NomatAuthenticationSuccessHandler,
    private val tokenAuthenticationFilter: TokenAuthenticationFilter,
    private val mdcLoggingFilter: MDCLoggingFilter,
    private val httpCookieOAuth2AuthorizationRequestRepository: HttpCookieOAuth2AuthorizationRequestRepository,
) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        return http.authorizeHttpRequests {
            it.requestMatchers(*permittedUrls.toTypedArray()).permitAll()
                .anyRequest().authenticated()
        }.oauth2Login { oauth2Login ->
            oauth2Login.authorizationEndpoint { }
                .userInfoEndpoint { userInfoEndpoint -> userInfoEndpoint.userService(nomatOAuth2UserService) }
                .authorizationEndpoint {
                    it.authorizationRequestRepository(
                        httpCookieOAuth2AuthorizationRequestRepository
                    )
                }
                .successHandler(nomatAuthenticationSuccessHandler)
        }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .csrf { it.disable() }
            .cors {  }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling { it.authenticationEntryPoint(Http403ForbiddenEntryPoint()) }
            .addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(mdcLoggingFilter, TokenAuthenticationFilter::class.java)
            .build()
    }

    companion object {
        val permittedUrls = setOf("/login/**", "/html/**", "/ws/**")
    }
}
