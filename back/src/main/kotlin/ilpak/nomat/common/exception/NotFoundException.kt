package ilpak.nomat.common.exception

import org.springframework.http.HttpStatus

class NotFoundException(val resource: NotFoundResource) :
    AbstractNomatException("${resource.name.lowercase()} not found", HttpStatus.NOT_FOUND)

enum class NotFoundResource {
    PLAYER,
    ROOM,
    PLAYLIST,
}
