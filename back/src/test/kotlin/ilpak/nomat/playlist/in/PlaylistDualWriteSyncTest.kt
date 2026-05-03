package ilpak.nomat.playlist.`in`

import ilpak.nomat.infrastructure.integration.IntegrationTest
import ilpak.nomat.infrastructure.integration.step.PlayerStep
import ilpak.nomat.infrastructure.integration.step.PlaylistStep
import ilpak.nomat.infrastructure.integration.step.dummyPlayerRequest
import ilpak.nomat.infrastructure.integration.step.dummyPlaylistCreationRequest
import ilpak.nomat.infrastructure.integration.util.auth
import ilpak.nomat.player.application.dto.PlayerResponse
import ilpak.nomat.playlist.application.dto.PlaylistCreationRequest
import ilpak.nomat.playlist.application.dto.PlaylistCreationRequestTrack
import ilpak.nomat.playlist.out.document.PlaylistDocument
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Duration

@IntegrationTest
class PlaylistDualWriteSyncTest(
    @Autowired private val client: WebTestClient,
    @Autowired private val playerStep: PlayerStep,
    @Autowired private val playlistStep: PlaylistStep,
    @Autowired private val operations: ElasticsearchOperations,
) {
    private lateinit var playerResponse: PlayerResponse

    @BeforeEach
    fun setUp() {
        playerResponse = playerStep.save(dummyPlayerRequest())
    }

    @Test
    fun `dual-write 환경에서 빠른 생성-수정 후 ES 최종 상태가 일관`() {
        val playlist = playlistStep.save(
            playerResponse,
            dummyPlaylistCreationRequest(title = "초기 제목", description = "초기 설명"),
        )

        client.put().uri("/playlists/${playlist.id}")
            .auth(playerResponse)
            .bodyValue(
                PlaylistCreationRequest(
                    title = "최종 제목",
                    description = "최종 설명",
                    tracks = listOf(
                        PlaylistCreationRequestTrack(
                            embedId = "abc",
                            title = "트랙",
                            startTimeSec = 0,
                            endTimeSec = 100,
                            repeatCount = 1,
                            additionalTitles = setOf(),
                            isRepresentative = true,
                        )
                    )
                )
            )
            .exchange()
            .expectStatus().isOk

        await()
            .pollDelay(Duration.ofSeconds(1))
            .pollInterval(Duration.ofSeconds(1))
            .atMost(Duration.ofSeconds(EVENTUAL_CONSISTENCY_SECONDS))
            .untilAsserted {
                val document = operations.get(playlist.id.toString(), PlaylistDocument::class.java)
                assertThat(document).isNotNull
                assertThat(document!!.title).isEqualTo("최종 제목")
                assertThat(document.description).isEqualTo("최종 설명")
            }
    }

    companion object {
        private const val EVENTUAL_CONSISTENCY_SECONDS = 15L
    }
}
