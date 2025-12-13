package ilpak.nomat.common.exception

import org.springframework.http.HttpStatus

abstract class AbstractNomatException(message: String, val httpStatus: HttpStatus) : Exception(message)
