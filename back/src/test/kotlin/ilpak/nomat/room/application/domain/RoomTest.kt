package ilpak.nomat.room.application.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RoomTest {
    @Test
    fun `create_최초 방 생성 시 방 상태는 PENDING`() {
        val room = Room(
            title = "room",
            playlist = RoomPlaylist(
                id = 1,
                masterId = 1,
                title = "playlist",
                description = "description",
            ),
            password = null,
            maxEntriesCount = 5,
        )

        assertThat(room.status).isEqualTo(RoomStatus.PENDING)
    }
}
