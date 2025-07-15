package ilpak.nomat.infrastructure.web

import ilpak.nomat.infrastructure.exception.AbstractNomatException
import ilpak.nomat.infrastructure.exception.InternalServerErrorException
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.ResponseStatus

private val logger = KotlinLogging.logger { }

@ControllerAdvice
private class GlobalControllerAdvice {

    @ExceptionHandler(InternalServerErrorException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    fun internalServerErrorException(exception: InternalServerErrorException): ExceptionResponse {
        logger.error(exception) { "Internal server error occurred" }
        return ExceptionResponse("서버에 에러가 발생했습니다. 잠시 후 다시 시도해주세요.")
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

    @ExceptionHandler(ConstraintViolationException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    fun constraintViolationException(exception: ConstraintViolationException): ExceptionResponse {
        val message = exception.constraintViolations.joinToString(", ") { it.message }
        return ExceptionResponse(message)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    fun httpMessageNotReadableException(exception: HttpMessageNotReadableException): ExceptionResponse {
        return ExceptionResponse("잘못된 요청입니다. 요청 형식을 확인해주세요.")
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    @ResponseBody
    fun httpRequestMethodNotSupportedException(exception: HttpRequestMethodNotSupportedException): ExceptionResponse {
        return ExceptionResponse("지원하지 않는 HTTP 메소드입니다.")
    }

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    fun handleException(exception: Exception): ExceptionResponse {
        logger.error(exception) { "Internal server error occurred" }
        return ExceptionResponse("서버에 에러가 발생했습니다. 잠시 후 다시 시도해주세요.")
    }
}

private data class ExceptionResponse(
    val message: String
)
