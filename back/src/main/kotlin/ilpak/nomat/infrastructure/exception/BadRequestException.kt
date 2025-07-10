package ilpak.nomat.infrastructure.exception

import org.springframework.http.HttpStatus

class BadRequestException(message: String): AbstractNomatException(message, HttpStatus.BAD_REQUEST)
