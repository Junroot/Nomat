package ilpak.nomat.room.application.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RoomChatRequest(
    @field:NotBlank(message = "채팅 메시지는 비어있을 수 없습니다.")
    @field:Size(max = MAX_CONTENT_LENGTH, message = "채팅 메시지는 ${MAX_CONTENT_LENGTH}자 이하이어야 합니다.")
    val content: String,
) {
    companion object {
        const val MAX_CONTENT_LENGTH = 200
    }
}
