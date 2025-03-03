package ilpak.nomat.infrastructure.web

import ilpak.nomat.infrastructure.exception.AbstractNomatException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
private class GlobalControllerAdvice {

    @ExceptionHandler(AbstractNomatException::class)
    fun nomatException(exception: AbstractNomatException): ResponseEntity<ExceptionResponse> {
        return ResponseEntity.status(exception.httpStatus)
            .body(ExceptionResponse(exception.message ?: ""))
    }
}

private data class ExceptionResponse(
    val message: String
)
