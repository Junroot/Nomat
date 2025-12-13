package ilpak.nomat.common.exception

import org.springframework.http.HttpStatus

class ConflictException(message: String): AbstractNomatException(message, HttpStatus.CONFLICT)
