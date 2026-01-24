package ilpak.nomat.infrastructure.websocket

import org.springframework.core.MethodParameter
import org.springframework.messaging.Message
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.security.core.annotation.AuthenticationPrincipal
import kotlin.jvm.java

class NomatAuthenticationPrincipalArgumentResolver: HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return parameter.getParameterAnnotation(AuthenticationPrincipal::class.java) != null
    }

    override fun resolveArgument(
        parameter: MethodParameter,
        message: Message<*>
    ): Long? {
        val user = SimpMessageHeaderAccessor.getUser(message.headers)
        return user?.name?.toLongOrNull()
    }
}
