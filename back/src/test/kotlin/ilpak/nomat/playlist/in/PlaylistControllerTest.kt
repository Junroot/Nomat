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
import org.springframework.http.MediaType
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
                            additionalTitles = listOf("Monster", "Kaibutsu"),
                            isRepresentative = true,
                        ),
                        PlaylistCreationRequestTrack(
                            embedId = "07SWfNXgKGo",
                            title = "삼원색",
                            startTimeSec = 0,
                            endTimeSec = 200,
                            repeatCount = 1,
                            additionalTitles = listOf(),
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
                    .ignoringFields("id", "tracks.id")
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
                                    id = 0,
                                ),
                                PlaylistWithTrackTrackResponse(
                                    embedId = "07SWfNXgKGo",
                                    title = "삼원색",
                                    startTimeSec = 0,
                                    endTimeSec = 200,
                                    repeatCount = 1,
                                    additionalTitles = setOf(),
                                    isRepresentative = false,
                                    id = 0,
                                )
                            ),
                        )
                    )
            }
    }

    @Test
    fun update() {
        val playlist = playlistStep.save(
            playerResponse,
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
                        additionalTitles = listOf("Monster", "Kaibutsu"),
                        isRepresentative = true,
                    ),
                    PlaylistCreationRequestTrack(
                        embedId = "07SWfNXgKGo",
                        title = "삼원색",
                        startTimeSec = 0,
                        endTimeSec = 200,
                        repeatCount = 1,
                        additionalTitles = listOf(),
                        isRepresentative = false,
                    )
                )
            )
        )

        client.put().uri("/playlists/${playlist.id}")
            .auth(playerResponse)
            .bodyValue(
                PlaylistCreationRequest(
                    title = "요아소비 플리 수정본",
                    description = "수정된 플레이리스트 입니다.",
                    tracks = listOf(
                        PlaylistCreationRequestTrack(
                            embedId = "07SWfNXgKGo",
                            title = "삼원색 수정본",
                            startTimeSec = 0,
                            endTimeSec = 20,
                            repeatCount = 3,
                            additionalTitles = listOf("RGB"),
                            isRepresentative = true,
                        )
                    )
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody<PlaylistWithTrackResponse>()
            .value {
                assertThat(it).usingRecursiveComparison()
                    .ignoringCollectionOrder()
                    .ignoringFields("id", "tracks.id")
                    .isEqualTo(
                        PlaylistWithTrackResponse(
                            id = playlist.id,
                            title = "요아소비 플리 수정본",
                            description = "수정된 플레이리스트 입니다.",
                            master = PlaylistWithTrackResponseMaster(
                                id = playerResponse.id,
                                nickname = playerResponse.nickname,
                            ),
                            tracks = listOf(
                                PlaylistWithTrackTrackResponse(
                                    embedId = "07SWfNXgKGo",
                                    title = "삼원색 수정본",
                                    startTimeSec = 0,
                                    endTimeSec = 20,
                                    repeatCount = 3,
                                    additionalTitles = setOf("RGB"),
                                    isRepresentative = true,
                                    id = 0,
                                )
                            ),
                        )
                    )
            }
    }

    @Test
    fun delete() {
        val playlistResponse = playlistStep.save(playerResponse, dummyPlaylistCreationRequest(title = "밤을달리다"))

        client.delete().uri("/playlists/${playlistResponse.id}")
            .auth(playerResponse)
            .exchange()
            .expectStatus().isOk()
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
                assertThat(it.favorite).isFalse()
            }
    }

    @Test
    fun getWithTracks() {
        val response = playlistStep.save(
            playerResponse,
            dummyPlaylistCreationRequest(
                title = "밤을 달리다",
                tracks = listOf(
                    PlaylistCreationRequestTrack(
                        embedId = "dy90tA3TT1c",
                        title = "괴물",
                        startTimeSec = 0,
                        endTimeSec = 208,
                        repeatCount = 2,
                        additionalTitles = listOf("Monster", "Kaibutsu"),
                        isRepresentative = true,
                    ),
                    PlaylistCreationRequestTrack(
                        embedId = "07SWfNXgKGo",
                        title = "삼원색",
                        startTimeSec = 0,
                        endTimeSec = 200,
                        repeatCount = 1,
                        additionalTitles = listOf(),
                        isRepresentative = false,
                    )
                )
            )
        )

        client.get().uri("/playlists/${response.id}?includeTracks=true")
            .auth(playerResponse)
            .exchange()
            .expectStatus().isOk
            .expectBody<PlaylistWithTrackResponse>()
            .value {
                assertThat(it).usingRecursiveComparison()
                    .ignoringCollectionOrder()
                    .isEqualTo(response)
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
                            additionalTitles = listOf("Monster", "Kaibutsu"),
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

        client.get().uri { it.path("/playlists").queryParam("masterDisplayName", playerResponse.displayName).build() }
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

    @Test
    fun `save_탁점만 다른 추가 정답이 함께 저장된다`() {
        val playlist = playlistStep.save(playerResponse, additionalTitlesRequest("탁점 플리", "ハハ", "ババ"))

        assertStoredAdditionalTitles(playlist.id, "ハハ", "ババ")
    }

    @Test
    fun `save_완전히 같은 추가 정답을 중복 입력해도 하나만 저장된다`() {
        val playlist = client.post().uri("/playlists")
            .auth(playerResponse)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(DUPLICATE_ADDITIONAL_TITLE_BODY)
            .exchange()
            .expectStatus().isCreated
            .expectBody<PlaylistWithTrackResponse>()
            .returnResult()
            .responseBody!!

        assertStoredAdditionalTitles(playlist.id, "ハハ")
    }

    @Test
    fun `update_탁점만 다른 추가 정답이 함께 저장된다`() {
        val playlist = playlistStep.save(playerResponse, dummyPlaylistCreationRequest())

        client.put().uri("/playlists/${playlist.id}")
            .auth(playerResponse)
            .bodyValue(additionalTitlesRequest("탁점 플리", "ハハ", "ババ"))
            .exchange()
            .expectStatus().isOk

        assertStoredAdditionalTitles(playlist.id, "ハハ", "ババ")
    }

    @Test
    fun `save_가나 표기만 다른 추가 정답은 먼저 온 값만 저장된다`() {
        val playlist = playlistStep.save(
            playerResponse,
            additionalTitlesRequest("가나 플리", "マイウェイ", "まいうぇい"),
        )

        assertStoredAdditionalTitles(playlist.id, "マイウェイ")
    }

    @Test
    fun `save_구두점만 다른 추가 정답은 먼저 온 값만 저장된다`() {
        val playlist = playlistStep.save(
            playerResponse,
            additionalTitlesRequest("구두점 플리", "マイ・ウェイ", "マイウェイ"),
        )

        assertStoredAdditionalTitles(playlist.id, "マイ・ウェイ")
    }

    @Test
    fun `save_괄호 꼬리표가 다른 추가 정답은 둘 다 저장된다`() {
        val playlist = playlistStep.save(
            playerResponse,
            additionalTitlesRequest("꼬리표 플리", "Monster", "Monster (feat. X)"),
        )

        assertStoredAdditionalTitles(playlist.id, "Monster", "Monster (feat. X)")
    }

    @Test
    fun `save_추가 정답은 정규화 키가 아니라 입력 원문 그대로 저장된다`() {
        val playlist = playlistStep.save(
            playerResponse,
            additionalTitlesRequest("원문 보존 플리", "まいうぇい", "밤을 달리다!"),
        )

        assertStoredAdditionalTitles(playlist.id, "まいうぇい", "밤을 달리다!")
    }

    @Test
    fun `update_정규화 기준으로 중복인 추가 정답을 담아도 수정이 거부되지 않는다`() {
        val playlist = playlistStep.save(playerResponse, dummyPlaylistCreationRequest())

        client.put().uri("/playlists/${playlist.id}")
            .auth(playerResponse)
            .bodyValue(additionalTitlesRequest("레거시 중복 플리", "マイウェイ", "マイ・ウェイ"))
            .exchange()
            .expectStatus().isOk

        assertStoredAdditionalTitles(playlist.id, "マイウェイ")
    }

    private fun additionalTitlesRequest(playlistTitle: String, vararg additionalTitles: String) =
        dummyPlaylistCreationRequest(
            title = playlistTitle,
            tracks = listOf(
                PlaylistCreationRequestTrack(
                    embedId = "dy90tA3TT1c",
                    title = "괴물",
                    startTimeSec = 0,
                    endTimeSec = 208,
                    repeatCount = 2,
                    // 순서가 의미를 가진다 — 정규화 키가 겹치면 먼저 온 값이 남는다.
                    additionalTitles = listOf(*additionalTitles),
                    isRepresentative = true,
                )
            )
        )

    private fun assertStoredAdditionalTitles(playlistId: Long, vararg expected: String) {
        client.get().uri("/playlists/$playlistId?includeTracks=true")
            .auth(playerResponse)
            .exchange()
            .expectStatus().isOk
            .expectBody<PlaylistWithTrackResponse>()
            .value {
                assertThat(it.tracks.single().additionalTitles).containsExactlyInAnyOrder(*expected)
            }
    }

    companion object {
        private val DUPLICATE_ADDITIONAL_TITLE_BODY = """
            {
              "title": "중복 추가 정답 플리",
              "description": "같은 문자열을 두 번 담아 보낸 요청입니다.",
              "tracks": [
                {
                  "embedId": "dy90tA3TT1c",
                  "title": "괴물",
                  "startTimeSec": 0,
                  "endTimeSec": 208,
                  "repeatCount": 2,
                  "additionalTitles": ["ハハ", "ハハ"],
                  "isRepresentative": true
                }
              ]
            }
        """.trimIndent()
    }
}
