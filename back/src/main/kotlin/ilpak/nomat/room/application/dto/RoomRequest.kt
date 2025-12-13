package ilpak.nomat.room.application.dto

import ilpak.nomat.room.application.domain.Room
import org.hibernate.validator.constraints.Length
import org.hibernate.validator.constraints.Range

data class RoomRequest(
    @field:Length(min = 1, max = Room.MAX_TITLE_LENGTH, message = "방 제목은 {min}자 이상 {max}자 이하이어야 합니다.")
    val title: String,
    @field:Length(min = 1, max = Room.MAX_PASSWORD_LENGTH, message = "비밀번호는 {min}자 이상 {max}자 이하이어야 합니다.")
    val password: String?,
    @field:Range(min = 1, max = Room.MAX_MAX_ENTRIES_COUNT.toLong(), message = "최대 인원수는 {min}명 이상 {max}명 이하이어야 합니다.")
    val maxEntriesCount: Int,
    val playlistId: Long,
)
