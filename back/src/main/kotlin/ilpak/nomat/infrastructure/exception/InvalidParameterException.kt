package ilpak.nomat.infrastructure.exception

import org.springframework.http.HttpStatus

class InvalidParameterException(message: String): AbstractNomatException(message, HttpStatus.BAD_REQUEST)
