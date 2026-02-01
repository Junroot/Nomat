package ilpak.nomat.room.application.dto

import ilpak.nomat.room.application.domain.Room
import ilpak.nomat.room.application.domain.RoomEntries
import ilpak.nomat.room.application.domain.RoomEntry
import ilpak.nomat.room.application.domain.RoomPlaylist
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class RoomDetailResponseTest {

    @Test
    fun `of_여러 플레이어가 있는 방에서 RoomDetailResponse를 생성한다`() {
        val room = Room(
            title = "Room",
            playlist = RoomPlaylist(
                id = 2L,
                title = "Playlist",
                masterId = 1L,
                description = "Description"
            ),
            password = null,
            maxEntriesCount = 10
        )

        val response = RoomDetailResponse.of(
            room = room,
            trackCount = 5,
            entries = RoomEntries(
                listOf(
                    RoomEntry(1L, LocalDateTime.now().minusHours(1)),
                    RoomEntry(2L, LocalDateTime.now()),
                    RoomEntry(3L, LocalDateTime.now()),
                )
            ),
            playerIdToNicknameMap = mapOf(
                1L to "Master", 2L to "Player2", 3L to "Player3"
            )
        )

        assertThat(response.players).hasSize(3)
        assertThat(response.players[0].nickname).isEqualTo("Master")
        assertThat(response.players[0].isMaster).isTrue()
        assertThat(response.players[1].nickname).isEqualTo("Player2")
        assertThat(response.players[1].isMaster).isFalse()
        assertThat(response.players[2].nickname).isEqualTo("Player3")
        assertThat(response.players[2].isMaster).isFalse()
    }

    @Test
    fun `of_플레이어 닉네임이 맵에 없을 때 해당 플레이어는 응답에서 제외된다`() {
        val room = Room(
            title = "Test Room",
            playlist = RoomPlaylist(
                id = 1L,
                title = "Test Playlist",
                masterId = 1L,
                description = "Description"
            ),
            password = null,
            maxEntriesCount = 5
        )

        val response = RoomDetailResponse.of(
            room = room,
            trackCount = 10,
            entries = RoomEntries(
                listOf(
                    RoomEntry(1L, LocalDateTime.now().minusHours(1)),
                    RoomEntry(2L, LocalDateTime.now()),
                    RoomEntry(3L, LocalDateTime.now()),
                )
            ),
            playerIdToNicknameMap = mapOf(1L to "Player1", 3L to "Player3")
        )

        assertThat(response.players).hasSize(2)
        assertThat(response.players.map { it.id }).containsExactly(1L, 3L)
        assertThat(response.players.map { it.nickname }).containsExactly("Player1", "Player3")
    }
}

