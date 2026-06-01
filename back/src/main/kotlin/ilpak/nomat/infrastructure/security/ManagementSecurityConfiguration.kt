package ilpak.nomat.infrastructure.security

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

/**
 * 관리(actuator) 전용 SecurityFilterChain.
 *
 * dev 프로파일에서 actuator는 전용 관리 포트(8081)로 분리되며, 이 포트는 내부 네트워크
 * (컨테이너 내부 healthcheck·nginx reverse proxy·Alloy scrape) 전용이라 인증을 요구하지 않는다.
 * 메인 체인의 OAuth2/STATELESS/Http403ForbiddenEntryPoint가 적용되면 Alloy가 403을 맞으므로
 * `EndpointRequest.toAnyEndpoint()`로 매칭되는 별도 permitAll 체인을 둔다.
 *
 * 메인 [SecurityConfiguration.filterChain]보다 먼저 매칭되도록 @Order를 지정한다.
 */
@Configuration
@Profile("!test")
class ManagementSecurityConfiguration {

    @Bean
    @Order(1)
    fun managementFilterChain(http: HttpSecurity): SecurityFilterChain {
        return http.securityMatcher(EndpointRequest.toAnyEndpoint())
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .build()
    }
}
