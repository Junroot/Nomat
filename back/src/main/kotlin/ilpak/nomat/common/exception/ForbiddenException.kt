package ilpak.nomat.common.exception

import org.springframework.http.HttpStatus

class ForbiddenException(message: String) : AbstractNomatException(message, HttpStatus.FORBIDDEN)
