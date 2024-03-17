package ilpak.nomat.room.controller

import ilpak.nomat.integration.AbstractIntegrationTest
import ilpak.nomat.room.domain.Room
import ilpak.nomat.room.domain.RoomMember
import ilpak.nomat.room.domain.RoomPlaylist
import ilpak.nomat.room.domain.RoomRepository
import ilpak.nomat.room.dto.RoomRequest
import ilpak.nomat.room.dto.RoomResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.expectBody
import kotlin.test.assertNotNull

class RoomControllerTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var roomRepository: RoomRepository

    @BeforeEach
    override fun setUp() {
        super.setUp()
        roomRepository.save(
            Room(
                "들어오셈",
                null,
                listOf(RoomMember(1L, "ROOT#3465")),
                RoomPlaylist(1L, "오늘의 TOP 100: 일본", 100)
            )
        )
    }

    @Test
    fun `방 리스트 조회`() {
        client.get().uri("/rooms")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].title").isEqualTo("들어오셈")
            .jsonPath("$[0].playlist.id").isEqualTo(1)
            .jsonPath("$[0].playlist.name").isEqualTo("오늘의 TOP 100: 일본")
            .jsonPath("$[0].playlist.count").isEqualTo(100)
            .jsonPath("$[0].masterNickname").isEqualTo("ROOT#3465")
    }

    @Test
    fun `방 생성 및 조회`() {
        val result = client.post().uri("/rooms")
            .bodyValue(
                RoomRequest(
                    "테스트",
                    100,
                    null,
                    1L,
                )
            )
            .exchange()
            .expectStatus().isCreated()
            .expectBody<RoomResponse>()
            .returnResult()
            .responseBody

        assertNotNull(result)

        client.get().uri("/rooms/{roomId}", result.id)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(result.id)
            .jsonPath("$.title").isEqualTo(result.title)
    }
}
