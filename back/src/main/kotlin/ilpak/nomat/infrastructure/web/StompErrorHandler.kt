package ilpak.nomat.infrastructure.web

import ilpak.nomat.common.exception.AbstractNomatException
import org.springframework.messaging.Message
import org.springframework.stereotype.Component
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler

@Component
class StompErrorHandler : StompSubProtocolErrorHandler() {

    override fun handleClientMessageProcessingError(
        clientMessage: Message<ByteArray>?,
        ex: Throwable,
    ): Message<ByteArray>? {
        val cause = findNomatException(ex)
        return if (cause != null) {
            super.handleClientMessageProcessingError(clientMessage, cause)
        } else {
            super.handleClientMessageProcessingError(clientMessage, ex)
        }
    }

    private fun findNomatException(ex: Throwable): AbstractNomatException? {
        var current: Throwable? = ex
        while (current != null) {
            if (current is AbstractNomatException) return current
            current = current.cause
        }
        return null
    }
}
