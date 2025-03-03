package ilpak.nomat.infrastructure.exception

import org.springframework.http.HttpStatus

class NotFoundException(exceptionCode: ExceptionCode) :
    AbstractNomatException("${exceptionCode.name} not found", HttpStatus.NOT_FOUND)
