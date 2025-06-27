package ilpak.nomat.infrastructure.exception

import org.springframework.http.HttpStatus

abstract class AbstractNomatException(message: String, val httpStatus: HttpStatus): RuntimeException(message)
