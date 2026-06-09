package ilpak.nomat.room.application.`in`

import ilpak.nomat.infrastructure.integration.IntegrationTest
import ilpak.nomat.infrastructure.integration.step.PlayerStep
import ilpak.nomat.infrastructure.integration.step.PlaylistStep
import ilpak.nomat.infrastructure.integration.step.RoomStep
import ilpak.nomat.infrastructure.integration.step.dummyPlayerRequest
import ilpak.nomat.infrastructure.integration.step.dummyPlaylistCreationRequest
import ilpak.nomat.infrastructure.integration.step.dummyRoomRequest
import ilpak.nomat.infrastructure.integration.util.auth
import ilpak.nomat.player.application.dto.PlayerResponse
import ilpak.nomat.playlist.application.dto.PlaylistWithTrackResponse
import ilpak.nomat.room.application.domain.RoomStatus
import ilpak.nomat.room.application.dto.PlaylistDetailResponse
import ilpak.nomat.room.application.dto.RoomDetailResponse
import ilpak.nomat.room.application.dto.RoomRequest
import ilpak.nomat.room.application.dto.PlaylistResponse
import ilpak.nomat.room.application.dto.RoomResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody

@IntegrationTest
class RoomControllerTest(
    @Autowired private val client: WebTestClient,
    @Autowired private val playerStep: PlayerStep,
    @Autowired private val playlistStep: PlaylistStep,
    @Autowired private val roomStep: RoomStep,
) {
    private lateinit var player: PlayerResponse
    private lateinit var playlist: PlaylistWithTrackResponse

    @BeforeEach
    fun setUp() {
        player = playerStep.save(dummyPlayerRequest())
        playlist = playlistStep.save(player, dummyPlaylistCreationRequest())
    }

    @Test
    fun save() {
        client.post().uri("/rooms")
            .auth(player)
            .bodyValue(
                RoomRequest(
                    title = "Test Room",
                    password = "password",
                    maxEntriesCount = 10,
                    playlistId = playlist.id,
                )
            )
            .exchange()
            .expectStatus().isCreated()
            .expectBody<RoomDetailResponse>()
            .value {
                assertThat(it).usingRecursiveComparison()
                    .ignoringFields("id")
                    .isEqualTo(
                        RoomDetailResponse(
                            id = 0L,
                            title = "Test Room",
                            playlist = PlaylistDetailResponse(
                                id = playlist.id,
                                title = playlist.title,
                                count = playlist.tracks.size,
                                master = player.nickname,
                                description = playlist.description,
                            ),
                            players = emptyList(),
                            status = RoomStatus.PENDING,
                        )
                    )
            }
    }

    @Test
    fun `save_존재하지 않는 플레이리스트로 방 생성 시도`() {
        client.post().uri("/rooms")
            .auth(player)
            .bodyValue(
                RoomRequest(
                    title = "Test Room",
                    password = "password",
                    maxEntriesCount = 10,
                    playlistId = playlist.id + 9999L,
                )
            )
            .exchange()
            .expectStatus().isBadRequest()
    }

    @Test
    fun getDetail() {
        val roomDetailResponse = roomStep.save(player, dummyRoomRequest(playlist.id))
        roomStep.join(player.id, roomDetailResponse.id, "password")

        client.get().uri("/rooms/{roomId}", roomDetailResponse.id)
            .auth(player)
            .exchange()
            .expectStatus().isOk()
            .expectBody<RoomDetailResponse>()
            .value {
                assertThat(it.id).isEqualTo(roomDetailResponse.id)
                assertThat(it.title).isEqualTo(roomDetailResponse.title)
                assertThat(it.players).hasSize(1)
            }
    }

    @Test
    fun `getDetail_방 멤버가 아닌 플레이어는 조회 불가`() {
        val roomDetailResponse = roomStep.save(player, dummyRoomRequest(playlist.id))
        val nonMember = playerStep.save(dummyPlayerRequest(nickname = "nonMember", registrationId = "nonMemberId"))

        client.get().uri("/rooms/{roomId}", roomDetailResponse.id)
            .auth(nonMember)
            .exchange()
            .expectStatus().isForbidden()
    }

    @Test
    fun `getDetail_응답에 현재 방 상태가 포함된다`() {
        val room = roomStep.save(player, dummyRoomRequest(playlist.id))
        roomStep.join(player.id, room.id, "password")

        client.get().uri("/rooms/{roomId}", room.id)
            .auth(player)
            .exchange()
            .expectStatus().isOk()
            .expectBody<RoomDetailResponse>()
            .value {
                assertThat(it.status).isEqualTo(RoomStatus.ACTIVE)
            }
    }

    @Test
    fun `getDetail_게임 중인 방의 상태는 PLAYING이다`() {
        val room = roomStep.save(player, dummyRoomRequest(playlist.id))
        roomStep.join(player.id, room.id, "password")
        roomStep.start(player.id, room.id)

        client.get().uri("/rooms/{roomId}", room.id)
            .auth(player)
            .exchange()
            .expectStatus().isOk()
            .expectBody<RoomDetailResponse>()
            .value {
                assertThat(it.status).isEqualTo(RoomStatus.PLAYING)
            }
    }

    @Test
    fun `get_PLAYING 상태인 방은 목록에서 제외된다`() {
        val room = roomStep.save(player, dummyRoomRequest(playlist.id))
        roomStep.join(player.id, room.id, "password")
        roomStep.start(player.id, room.id)

        client.get().uri("/rooms")
            .auth(player)
            .exchange()
            .expectStatus().isOk()
            .expectBody<List<RoomResponse>>()
            .value {
                assertThat(it).isEmpty()
            }
    }

    @Test
    fun `get_active 상태인 방만 조회`() {
        val roomDetailResponse = roomStep.save(player, dummyRoomRequest(playlist.id))

        client.get().uri("/rooms", roomDetailResponse.id)
            .auth(player)
            .exchange()
            .expectStatus().isOk()
            .expectBody<List<RoomResponse>>()
            .value {
                assertThat(it).isEmpty()
            }
    }

    @Test
    fun `get_방 목록 응답에 추가 필드가 포함된다`() {
        val room = roomStep.save(player, dummyRoomRequest(playlist.id))
        roomStep.join(player.id, room.id, "password")

        client.get().uri("/rooms")
            .auth(player)
            .exchange()
            .expectStatus().isOk()
            .expectBody<List<RoomResponse>>()
            .value { rooms ->
                assertThat(rooms).hasSize(1)
                val roomResponse = rooms[0]
                assertThat(roomResponse).usingRecursiveComparison()
                    .ignoringFields("id")
                    .isEqualTo(
                        RoomResponse(
                            id = 0L,
                            title = "Test Room",
                            playlist = PlaylistResponse(
                                title = playlist.title,
                                trackCount = playlist.tracks.size,
                                id = playlist.id,
                            ),
                            masterDisplayName = player.displayName,
                            hasPassword = true,
                            maxPlayerCount = 10,
                            currentPlayerCount = 1,
                            representativeTrackEmbedId = playlist.tracks.first { it.isRepresentative }.embedId,
                        )
                    )
            }
    }
}
