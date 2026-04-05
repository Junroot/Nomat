package ilpak.nomat.infrastructure.web

import ilpak.nomat.common.exception.AbstractNomatException
import org.springframework.messaging.Message
import org.springframework.stereotype.Component
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler

@Component
class StompErrorHandler : StompSubProtocolErrorHandler() {

    override fun handleClientMessageProcessingError(
        clientMessage: Message<ByteArray>?,
        ex: Throwable,
    ): Message<ByteArray>? {
        val validationException = findException<MethodArgumentNotValidException>(ex)
        if (validationException != null) {
            val message = validationException.bindingResult.fieldErrors
                .joinToString(", ") { "${it.defaultMessage}" }
            return super.handleClientMessageProcessingError(clientMessage, RuntimeException(message))
        }

        val nomatException = findException<AbstractNomatException>(ex)
        return if (nomatException != null) {
            super.handleClientMessageProcessingError(clientMessage, nomatException)
        } else {
            super.handleClientMessageProcessingError(clientMessage, ex)
        }
    }

    private inline fun <reified T : Throwable> findException(ex: Throwable): T? {
        var current: Throwable? = ex
        while (current != null) {
            if (current is T) return current
            current = current.cause
        }
        return null
    }
}
