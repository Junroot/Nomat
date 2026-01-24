package ilpak.nomat.room.application.dto

data class RoomChatRequest(
    val command: Command,
) {
    enum class Command {
        JOIN,
    }
}
