package ilpak.nomat.room.controller

import ilpak.nomat.integration.AbstractIntegrationTest
import ilpak.nomat.player.application.PlayerService
import ilpak.nomat.player.application.domain.RegistrationType
import ilpak.nomat.player.application.dto.PlayerRequest
import ilpak.nomat.room.application.dto.RoomDetailResponse
import ilpak.nomat.room.application.dto.RoomRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.expectBody
import kotlin.test.assertNotNull

class RoomControllerTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var playerService: PlayerService

    @BeforeEach
    override fun setUp() {
        super.setUp()
        playerService.save(
            PlayerRequest(
                nickname = "ROOT#3465",
                registrationType = RegistrationType.DISCORD,
                registrationId = "abc"
            )
        )
    }

    @Test
    fun `방 생성, 리스트 조회, 상세 조회`() {
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
            .expectBody<RoomDetailResponse>()
            .returnResult()
            .responseBody

        assertNotNull(result)

        client.get().uri("/rooms/{roomId}", result.id)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.id").isEqualTo(result.id)
            .jsonPath("$.title").isEqualTo(result.title)
            .jsonPath("$.playlist.id").isEqualTo(1L)
            .jsonPath("$.playlist.name").isEqualTo("오늘의 TOP 100: 일본")
            .jsonPath("$.playlist.count").isEqualTo(100)
            .jsonPath("$.playlist.master").isEqualTo("ROOT#3465")
            .jsonPath("$.playlist.comment").isNotEmpty()
            .jsonPath("$.players.length()").isEqualTo(1)
            .jsonPath("$.players[0].nickname").isEqualTo("ROOT#3465")
            .jsonPath("$.players[0].isMaster").isEqualTo(true)

        client.get().uri("/rooms")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].title").isEqualTo(result.title)
            .jsonPath("$[0].playlist.id").isEqualTo(1)
            .jsonPath("$[0].playlist.name").isEqualTo(result.playlist.name)
            .jsonPath("$[0].playlist.count").isEqualTo(result.playlist.count)
            .jsonPath("$[0].masterNickname").isEqualTo("ROOT#3465")
    }
}
