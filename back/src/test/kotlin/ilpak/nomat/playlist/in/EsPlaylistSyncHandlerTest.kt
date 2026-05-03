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
class EsPlaylistSyncHandlerTest(
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
    fun `생성 시 ES upsert`() {
        val playlist = playlistStep.save(
            playerResponse,
            dummyPlaylistCreationRequest(title = "요아소비", description = "요아소비 모음"),
        )

        await()
            .atMost(Duration.ofSeconds(SYNC_TIMEOUT_SECONDS))
            .untilAsserted {
                val document = operations.get(playlist.id.toString(), PlaylistDocument::class.java)
                assertThat(document).isNotNull
                assertThat(document!!.title).isEqualTo("요아소비")
                assertThat(document.description).isEqualTo("요아소비 모음")
            }
    }

    @Test
    fun `수정 시 ES upsert`() {
        val playlist = playlistStep.save(
            playerResponse,
            dummyPlaylistCreationRequest(title = "옛 제목", description = "옛 설명"),
        )

        await()
            .atMost(Duration.ofSeconds(SYNC_TIMEOUT_SECONDS))
            .untilAsserted {
                val document = operations.get(playlist.id.toString(), PlaylistDocument::class.java)
                assertThat(document?.title).isEqualTo("옛 제목")
            }

        client.put().uri("/playlists/${playlist.id}")
            .auth(playerResponse)
            .bodyValue(
                PlaylistCreationRequest(
                    title = "새 제목",
                    description = "새 설명",
                    tracks = listOf(
                        PlaylistCreationRequestTrack(
                            embedId = "abc123",
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
            .atMost(Duration.ofSeconds(SYNC_TIMEOUT_SECONDS))
            .untilAsserted {
                val document = operations.get(playlist.id.toString(), PlaylistDocument::class.java)
                assertThat(document?.title).isEqualTo("새 제목")
                assertThat(document?.description).isEqualTo("새 설명")
            }
    }

    @Test
    fun `삭제 시 ES 문서 제거`() {
        val playlist = playlistStep.save(playerResponse, dummyPlaylistCreationRequest(title = "삭제 대상"))

        await()
            .atMost(Duration.ofSeconds(SYNC_TIMEOUT_SECONDS))
            .untilAsserted {
                assertThat(operations.get(playlist.id.toString(), PlaylistDocument::class.java)).isNotNull
            }

        client.delete().uri("/playlists/${playlist.id}")
            .auth(playerResponse)
            .exchange()
            .expectStatus().isOk

        await()
            .atMost(Duration.ofSeconds(SYNC_TIMEOUT_SECONDS))
            .untilAsserted {
                assertThat(operations.get(playlist.id.toString(), PlaylistDocument::class.java)).isNull()
            }
    }

    companion object {
        private const val SYNC_TIMEOUT_SECONDS = 10L
    }
}
