package ilpak.nomat.playlist.`in`

import ilpak.nomat.infrastructure.integration.IntegrationTest
import ilpak.nomat.infrastructure.integration.step.FavoritePlaylistStep
import ilpak.nomat.infrastructure.integration.step.PlayerStep
import ilpak.nomat.infrastructure.integration.step.PlaylistStep
import ilpak.nomat.infrastructure.integration.step.dummyPlayerRequest
import ilpak.nomat.infrastructure.integration.step.dummyPlaylistCreationRequest
import ilpak.nomat.infrastructure.integration.util.auth
import ilpak.nomat.player.application.dto.PlayerResponse
import ilpak.nomat.playlist.application.dto.PlaylistCreationRequest
import ilpak.nomat.playlist.application.dto.PlaylistCreationRequestTrack
import ilpak.nomat.playlist.application.dto.PlaylistMetaDataResponse
import ilpak.nomat.playlist.application.dto.PlaylistMetaDataResponseMaster
import ilpak.nomat.playlist.application.dto.PlaylistMetaDataResponseTrack
import ilpak.nomat.playlist.application.dto.PlaylistResponse
import ilpak.nomat.playlist.application.dto.PlaylistWithTrackResponse
import ilpak.nomat.playlist.application.dto.PlaylistWithTrackResponseMaster
import ilpak.nomat.playlist.application.dto.PlaylistWithTrackTrackResponse
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import java.time.Duration

@IntegrationTest
class PlaylistControllerTest(
    @Autowired private val client: WebTestClient,
    @Autowired private val playerStep: PlayerStep,
    @Autowired private val playlistStep: PlaylistStep,
    @Autowired private val favoritePlaylistStep: FavoritePlaylistStep,
) {
    private lateinit var playerResponse: PlayerResponse

    @BeforeEach
    fun setUp() {
        playerResponse = playerStep.save(dummyPlayerRequest())
    }

    @Test
    fun save() {
        client.post().uri("/playlists")
            .auth(playerResponse)
            .bodyValue(
                PlaylistCreationRequest(
                    title = "요아소비 플리",
                    description = "저의 최애 아티스트인 요아소비의 플레이리스트 입니다.",
                    tracks = listOf(
                        PlaylistCreationRequestTrack(
                            embedId = "dy90tA3TT1c",
                            title = "괴물",
                            startTimeSec = 0,
                            endTimeSec = 208,
                            repeatCount = 2,
                            additionalTitles = setOf("Monster", "Kaibutsu"),
                            isRepresentative = true,
                        ),
                        PlaylistCreationRequestTrack(
                            embedId = "07SWfNXgKGo",
                            title = "삼원색",
                            startTimeSec = 0,
                            endTimeSec = 200,
                            repeatCount = 1,
                            additionalTitles = setOf(),
                            isRepresentative = false,
                        )
                    )
                )
            )
            .exchange()
            .expectStatus().isCreated
            .expectBody<PlaylistWithTrackResponse>()
            .value {
                assertThat(it).usingRecursiveComparison()
                    .ignoringCollectionOrder()
                    .ignoringFields("id")
                    .isEqualTo(
                        PlaylistWithTrackResponse(
                            id = 0,
                            title = "요아소비 플리",
                            description = "저의 최애 아티스트인 요아소비의 플레이리스트 입니다.",
                            master = PlaylistWithTrackResponseMaster(
                                id = playerResponse.id,
                                nickname = playerResponse.nickname,
                            ),
                            tracks = listOf(
                                PlaylistWithTrackTrackResponse(
                                    embedId = "dy90tA3TT1c",
                                    title = "괴물",
                                    startTimeSec = 0,
                                    endTimeSec = 208,
                                    repeatCount = 2,
                                    additionalTitles = setOf("Monster", "Kaibutsu"),
                                    isRepresentative = true,
                                ),
                                PlaylistWithTrackTrackResponse(
                                    embedId = "07SWfNXgKGo",
                                    title = "삼원색",
                                    startTimeSec = 0,
                                    endTimeSec = 200,
                                    repeatCount = 1,
                                    additionalTitles = setOf(),
                                    isRepresentative = false,
                                )
                            ),
                        )
                    )
            }
    }

    @Test
    fun getById() {
        val response = playlistStep.save(playerResponse, dummyPlaylistCreationRequest(title = "밤을 달리다"))

        client.get().uri("/playlists/${response.id}")
            .auth(playerResponse)
            .exchange()
            .expectStatus().isOk
            .expectBody<PlaylistResponse>()
            .value {
                assertThat(it.id).isEqualTo(response.id)
                assertThat(it.title).isEqualTo(response.title)
                assertThat(it.description).isEqualTo(response.description)
                assertThat(it.master.id).isEqualTo(playerResponse.id)
                assertThat(it.master.nickname).isEqualTo(playerResponse.nickname)
                assertThat(it.representativeTrack.embedId).isEqualTo(response.tracks.first().embedId)
                assertThat(it.representativeTrack.startTimeSec).isEqualTo(response.tracks.first().startTimeSec)
                assertThat(it.representativeTrack.endTimeSec).isEqualTo(response.tracks.first().endTimeSec)
                assertThat(it.trackCount).isEqualTo(response.tracks.size)
                assertThat(it.expectedPlayTimeSec).isGreaterThan(0)
            }
    }

    @Test
    fun `save_플레이어는 1000개까지만 플레이리스트 생성 가능`() {
        repeat(1000) {
            playlistStep.save(playerResponse, dummyPlaylistCreationRequest(title = "플레이리스트 $it"))
        }

        client.post().uri("/playlists")
            .auth(playerResponse)
            .bodyValue(
                PlaylistCreationRequest(
                    title = "요아소비 플리",
                    description = "저의 최애 아티스트인 요아소비의 플레이리스트 입니다.",
                    tracks = listOf(
                        PlaylistCreationRequestTrack(
                            embedId = "dy90tA3TT1c",
                            title = "괴물",
                            startTimeSec = 0,
                            endTimeSec = 208,
                            repeatCount = 2,
                            additionalTitles = setOf("Monster", "Kaibutsu"),
                            isRepresentative = true,
                        )
                    )
                )
            )
            .exchange()
            .expectStatus().isForbidden()
    }

    @Test
    fun getMyPlaylists() {
        val playlistResponse = playlistStep.save(playerResponse, dummyPlaylistCreationRequest(title = "밤을달리다"))

        client.get().uri("/playlists?masterId=me")
            .auth(playerResponse)
            .exchange()
            .expectStatus().isOk
            .expectBody<List<PlaylistMetaDataResponse>>()
            .value {
                assertThat(it).usingRecursiveComparison()
                    .ignoringCollectionOrder()
                    .ignoringFields("id")
                    .isEqualTo(
                        listOf(
                            PlaylistMetaDataResponse(
                                id = playlistResponse.id,
                                title = playlistResponse.title,
                                representativeTrack = PlaylistMetaDataResponseTrack(
                                    embedId = playlistResponse.tracks.first().embedId,
                                    title = playlistResponse.tracks.first().title,
                                ),
                                description = playlistResponse.description,
                                master = PlaylistMetaDataResponseMaster(
                                    id = playerResponse.id,
                                    nickname = playerResponse.nickname,
                                    registrationType = playerResponse.registrationType,
                                    displayName = playerResponse.displayName
                                ),
                            )
                        )
                    )
            }
    }

    @Test
    fun getRecentlyAddedPlaylists() {
        repeat(5) {
            playlistStep.save(playerResponse, dummyPlaylistCreationRequest(title = "밤을달리다$it"))
        }

        client.get().uri("/playlists?sort=createdAt,desc&limit=3")
            .auth(playerResponse)
            .exchange()
            .expectStatus().isOk
            .expectBody<List<PlaylistMetaDataResponse>>()
            .value {
                assertThat(it.size).isEqualTo(3)
                assertThat(it[0].title).isEqualTo("밤을달리다4")
                assertThat(it[1].title).isEqualTo("밤을달리다3")
                assertThat(it[2].title).isEqualTo("밤을달리다2")
            }
    }

    @Test
    fun searchByTitle() {
        val playlistResponse = playlistStep.save(playerResponse, dummyPlaylistCreationRequest(title = "밤을달리다"))

        await()
            .pollDelay(Duration.ofSeconds(1))
            .pollInterval(Duration.ofSeconds(1))
            .atMost(Duration.ofSeconds(5))
            .untilAsserted {
                client.get().uri("/playlists?title=밤에달리다")
                    .auth(playerResponse)
                    .exchange()
                    .expectStatus().isOk
                    .expectBody<List<PlaylistMetaDataResponse>>()
                    .value {
                        assertThat(it).usingRecursiveComparison()
                            .ignoringCollectionOrder()
                            .ignoringFields("id")
                            .isEqualTo(
                                listOf(
                                    PlaylistMetaDataResponse(
                                        id = playlistResponse.id,
                                        title = playlistResponse.title,
                                        representativeTrack = PlaylistMetaDataResponseTrack(
                                            embedId = playlistResponse.tracks.first().embedId,
                                            title = playlistResponse.tracks.first().title,
                                        ),
                                        description = playlistResponse.description,
                                        master = PlaylistMetaDataResponseMaster(
                                            id = playerResponse.id,
                                            nickname = playerResponse.nickname,
                                            registrationType = playerResponse.registrationType,
                                            displayName = playerResponse.displayName,
                                        ),
                                    )
                                )
                            )
                    }
            }
    }

    @Test
    fun getByMasterDisplayName() {
        val playlistResponse = playlistStep.save(playerResponse, dummyPlaylistCreationRequest(title = "밤을달리다"))

        client.get().uri{ it.path("/playlists").queryParam("masterDisplayName", playerResponse.displayName).build() }
            .auth(playerResponse)
            .exchange()
            .expectStatus().isOk
            .expectBody<List<PlaylistMetaDataResponse>>()
            .value {
                assertThat(it).isEqualTo(
                    listOf(
                        PlaylistMetaDataResponse(
                            id = playlistResponse.id,
                            title = playlistResponse.title,
                            representativeTrack = PlaylistMetaDataResponseTrack(
                                embedId = playlistResponse.tracks.first().embedId,
                                title = playlistResponse.tracks.first().title,
                            ),
                            description = playlistResponse.description,
                            master = PlaylistMetaDataResponseMaster(
                                id = playerResponse.id,
                                nickname = playerResponse.nickname,
                                registrationType = playerResponse.registrationType,
                                displayName = playerResponse.displayName,
                            ),
                        )
                    )
                )
            }
    }

    @Test
    fun getFavoritePlaylistsOfMe() {
        val playlistResponse = playlistStep.save(playerResponse, dummyPlaylistCreationRequest(title = "밤을달리다"))
        playlistStep.save(playerResponse, dummyPlaylistCreationRequest(title = "봄망초"))
        favoritePlaylistStep.save(playerResponse, playlistResponse.id)

        client.get().uri("/playlists?favoriteOf=me")
            .auth(playerResponse)
            .exchange()
            .expectStatus().isOk
            .expectBody<List<PlaylistMetaDataResponse>>()
            .value {
                assertThat(it).usingRecursiveComparison()
                    .ignoringCollectionOrder()
                    .ignoringFields("id")
                    .isEqualTo(
                        listOf(
                            PlaylistMetaDataResponse(
                                id = playlistResponse.id,
                                title = playlistResponse.title,
                                representativeTrack = PlaylistMetaDataResponseTrack(
                                    embedId = playlistResponse.tracks.first().embedId,
                                    title = playlistResponse.tracks.first().title,
                                ),
                                description = playlistResponse.description,
                                master = PlaylistMetaDataResponseMaster(
                                    id = playerResponse.id,
                                    nickname = playerResponse.nickname,
                                    registrationType = playerResponse.registrationType,
                                    displayName = playerResponse.displayName
                                ),
                            )
                        )
                    )
            }

        favoritePlaylistStep.delete(playerResponse, playlistResponse.id)

        client.get().uri("/playlists?favoriteOf=me")
            .auth(playerResponse)
            .exchange()
            .expectStatus().isOk
            .expectBody<List<PlaylistMetaDataResponse>>()
            .value {
                assertThat(it).isEmpty()
            }
    }
}
