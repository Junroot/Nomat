package ilpak.nomat.infrastructure.exception

import org.springframework.http.HttpStatus

class InternalServerErrorException(message: String) : AbstractNomatException(message, HttpStatus.INTERNAL_SERVER_ERROR)
