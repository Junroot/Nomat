package ilpak.nomat.playlist.`in`

import ilpak.nomat.integration.IntegrationTest
import ilpak.nomat.integration.step.PlayerStep
import ilpak.nomat.integration.step.PlaylistStep
import ilpak.nomat.integration.step.dummyPlayerRequest
import ilpak.nomat.integration.step.dummyPlaylistCreationRequest
import ilpak.nomat.integration.util.auth
import ilpak.nomat.playlist.application.dto.PlaylistCreationRequest
import ilpak.nomat.playlist.application.dto.PlaylistCreationRequestTrack
import ilpak.nomat.playlist.application.dto.PlaylistResponse
import ilpak.nomat.playlist.application.dto.PlaylistTrackResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody

@IntegrationTest
class PlaylistControllerTest(
	@Autowired private val client: WebTestClient,
	@Autowired private val playerStep: PlayerStep,
	@Autowired private val playlistStep: PlaylistStep,
) {

	@Test
	fun save() {
		val playerResponse = playerStep.save(dummyPlayerRequest())

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
			.expectBody<PlaylistResponse>()
			.value {
				assertThat(it).usingRecursiveComparison()
					.ignoringCollectionOrder()
					.ignoringFields("id")
					.isEqualTo(
						PlaylistResponse(
							id = 0,
							title = "요아소비 플리",
							description = "저의 최애 아티스트인 요아소비의 플레이리스트 입니다.",
							masterId = playerResponse.id,
							tracks = listOf(
								PlaylistTrackResponse(
									embedId = "dy90tA3TT1c",
									title = "괴물",
									startTimeSec = 0,
									endTimeSec = 208,
									repeatCount = 2,
									additionalTitles = setOf("Monster", "Kaibutsu"),
									isRepresentative = true,
								),
								PlaylistTrackResponse(
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
	fun `save_플레이어는 100개까지만 플레이리스트 생성 가능`() {
		val playerResponse = playerStep.save(dummyPlayerRequest())
		repeat(100) {
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
}
