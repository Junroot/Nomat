package ilpak.nomat.room.application.domain

import ilpak.nomat.common.exception.ConflictException
import ilpak.nomat.common.exception.ForbiddenException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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

    @Test
    fun getSortedEntries() {
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
        room.join(2)
        room.join(1)

        assertThat(room.sortedEntries.map { it.playerId }).containsExactly(2, 1)
    }

    @Test
    fun `join_방 생성 된 후 최초 입장이 있으면 방 상태가 ACTIVE로 변경 됨`() {
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

        room.join(1)

        assertThat(room.status).isEqualTo(RoomStatus.ACTIVE)
    }

    @Test
    fun `join_방 정원 초과 시 예외 발생`(){
        val room = Room(
            title = "room",
            playlist = RoomPlaylist(
                id = 1,
                masterId = 1,
                title = "playlist",
                description = "description",
            ),
            password = null,
            maxEntriesCount = 2,
        )
        room.join(1)
        room.join(2)

        assertThatThrownBy { room.join(3) }
            .isExactlyInstanceOf(ConflictException::class.java)
    }

    @Test
    fun `verifyPassword_비밀번호가 없는 방은 검증을 통과한다`() {
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

        room.verifyPassword(null)
        room.verifyPassword("anything")
    }

    @Test
    fun `verifyPassword_비밀번호가 일치하면 검증을 통과한다`() {
        val room = Room(
            title = "room",
            playlist = RoomPlaylist(
                id = 1,
                masterId = 1,
                title = "playlist",
                description = "description",
            ),
            password = "secret",
            maxEntriesCount = 5,
        )

        room.verifyPassword("secret")
    }

    @Test
    fun `verifyPassword_비밀번호가 일치하지 않으면 예외가 발생한다`() {
        val room = Room(
            title = "room",
            playlist = RoomPlaylist(
                id = 1,
                masterId = 1,
                title = "playlist",
                description = "description",
            ),
            password = "secret",
            maxEntriesCount = 5,
        )

        assertThatThrownBy { room.verifyPassword("wrong") }
            .isExactlyInstanceOf(ForbiddenException::class.java)
    }

    @Test
    fun `verifyPassword_비밀번호가 있는 방에 비밀번호 없이 입장하면 예외가 발생한다`() {
        val room = Room(
            title = "room",
            playlist = RoomPlaylist(
                id = 1,
                masterId = 1,
                title = "playlist",
                description = "description",
            ),
            password = "secret",
            maxEntriesCount = 5,
        )

        assertThatThrownBy { room.verifyPassword(null) }
            .isExactlyInstanceOf(ForbiddenException::class.java)
    }

    @Test
    fun `join_이미 입장한 플레이어가 다시 입장 시도할 경우 예외 발생`() {
        val room = Room(
            title = "room",
            playlist = RoomPlaylist(
                id = 1,
                masterId = 1,
                title = "playlist",
                description = "description",
            ),
            password = null,
            maxEntriesCount = 3,
        )
        room.join(1)

        assertThatThrownBy { room.join(1) }
            .isExactlyInstanceOf(ConflictException::class.java)
    }

    @Test
    fun `leave_입장한 플레이어가 퇴장하면 entries에서 제거된다`() {
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
        room.join(1)
        room.join(2)

        room.leave(1)

        assertThat(room.playerIds).containsExactly(2L)
    }

    @Test
    fun `leave_입장하지 않은 플레이어가 퇴장 시도하면 아무 일도 발생하지 않는다`() {
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
        room.join(1)

        room.leave(999)

        assertThat(room.playerIds).containsExactly(1L)
    }

    @Test
    fun `leave_퇴장 후 다시 입장할 수 있다`() {
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
        room.join(1)
        room.leave(1)

        room.join(1)

        assertThat(room.playerIds).containsExactly(1L)
    }

    @Test
    fun `leave_모든 플레이어가 퇴장하면 방이 비어있다`() {
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
        room.join(1)
        room.join(2)

        room.leave(1)
        assertThat(room.isEmpty).isFalse()

        room.leave(2)
        assertThat(room.isEmpty).isTrue()
    }

    @Test
    fun `start_방장이 ACTIVE 상태에서 게임을 시작하면 PLAYING으로 전이된다`() {
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
        room.join(1)

        room.start(1)

        assertThat(room.status).isEqualTo(RoomStatus.PLAYING)
    }

    @Test
    fun `start_방장이 아닌 멤버가 시작하면 예외 발생`() {
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
        room.join(1)
        room.join(2)

        assertThatThrownBy { room.start(2) }
            .isExactlyInstanceOf(ForbiddenException::class.java)
        assertThat(room.status).isEqualTo(RoomStatus.ACTIVE)
    }

    @Test
    fun `start_이미 PLAYING 상태이면 예외 발생`() {
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
        room.join(1)
        room.start(1)

        assertThatThrownBy { room.start(1) }
            .isExactlyInstanceOf(ConflictException::class.java)
        assertThat(room.status).isEqualTo(RoomStatus.PLAYING)
    }

    @Test
    fun `end_방장이 PLAYING 상태에서 게임을 종료하면 ACTIVE로 전이된다`() {
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
        room.join(1)
        room.start(1)

        room.end(1)

        assertThat(room.status).isEqualTo(RoomStatus.ACTIVE)
    }

    @Test
    fun `end_방장이 아닌 멤버가 종료하면 예외 발생`() {
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
        room.join(1)
        room.join(2)
        room.start(1)

        assertThatThrownBy { room.end(2) }
            .isExactlyInstanceOf(ForbiddenException::class.java)
        assertThat(room.status).isEqualTo(RoomStatus.PLAYING)
    }

    @Test
    fun `end_PLAYING 상태가 아니면 예외 발생`() {
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
        room.join(1)

        assertThatThrownBy { room.end(1) }
            .isExactlyInstanceOf(ConflictException::class.java)
        assertThat(room.status).isEqualTo(RoomStatus.ACTIVE)
    }

    @Test
    fun `endByEngine_PLAYING이면 ACTIVE로 멱등 플립된다`() {
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
        room.join(1)
        room.start(1)

        room.endByEngine()

        assertThat(room.status).isEqualTo(RoomStatus.ACTIVE)
    }

    @Test
    fun `endByEngine_PLAYING이 아니면 상태를 바꾸지 않는다`() {
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
        room.join(1)

        room.endByEngine()

        assertThat(room.status).isEqualTo(RoomStatus.ACTIVE)
    }

    @Test
    fun `join_게임 중인 방에는 입장할 수 없다`() {
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
        room.join(1)
        room.start(1)

        assertThatThrownBy { room.join(2) }
            .isExactlyInstanceOf(ConflictException::class.java)
    }
}
