package ilpak.nomat.infrastructure.web

import ilpak.nomat.infrastructure.exception.AbstractNomatException
import ilpak.nomat.infrastructure.exception.InternalServerErrorException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.ResponseStatus

@ControllerAdvice
private class GlobalControllerAdvice {

    @ExceptionHandler(InternalServerErrorException::class)
    fun internalServerErrorException(exception: InternalServerErrorException): ResponseEntity<ExceptionResponse> {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.APPLICATION_JSON)
            .body(ExceptionResponse("서버에 에러가 발생했습니다. 잠시 후 다시 시도해주세요."))

    }

    @ExceptionHandler(AbstractNomatException::class)
    fun nomatException(exception: AbstractNomatException): ResponseEntity<ExceptionResponse> {
        return ResponseEntity.status(exception.httpStatus)
            .contentType(MediaType.APPLICATION_JSON)
            .body(ExceptionResponse(exception.message ?: ""))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    fun methodArgumentNotValidException(exception: MethodArgumentNotValidException): ExceptionResponse {
        val message = exception.bindingResult.fieldErrors.joinToString(", ") { "${it.defaultMessage}" }
        return ExceptionResponse(message)
    }
}

private data class ExceptionResponse(
    val message: String
)
