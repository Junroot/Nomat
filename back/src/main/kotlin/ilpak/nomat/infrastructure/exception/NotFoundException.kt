package ilpak.nomat.infrastructure.exception

import org.springframework.http.HttpStatus

class NotFoundException(resource: NotFoundResource) :
    AbstractNomatException("${resource.name.lowercase()} not found", HttpStatus.NOT_FOUND)

enum class NotFoundResource {
    PLAYER,
    ROOM,
    PLAYLIST,
}
