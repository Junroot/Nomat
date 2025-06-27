package ilpak.nomat.infrastructure.exception

import org.springframework.http.HttpStatus

class ForbiddenException(message: String): AbstractNomatException(message, HttpStatus.FORBIDDEN)
