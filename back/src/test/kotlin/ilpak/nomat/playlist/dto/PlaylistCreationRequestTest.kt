package ilpak.nomat.playlist.dto

import ilpak.nomat.common.exception.BadRequestException
import ilpak.nomat.infrastructure.validator.HibernateValidator
import ilpak.nomat.playlist.application.domain.Playlist
import ilpak.nomat.playlist.application.domain.Track
import ilpak.nomat.playlist.application.dto.PlaylistCreationRequest
import ilpak.nomat.playlist.application.dto.PlaylistCreationRequestTrack
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class PlaylistCreationRequestTest {

    @Test
    fun create() {
        val result = HibernateValidator.default.validate(getRequest())
        assertThat(result).isEmpty()
    }

    @ParameterizedTest
    @MethodSource("invalidTitle")
    fun invalidTitle(title: String) {
        val request = getRequest().copy(title = title)
        val result = HibernateValidator.default.validate(request)

        assertThat(result).hasSize(1)
        assertThat(result.first().message).isEqualTo("플레이리스트 제목은 1자 이상 ${Playlist.MAX_TITLE_LENGTH}자 이하이어야 합니다.")
    }

    @ParameterizedTest
    @MethodSource("invalidDescription")
    fun invalidDescription(description: String) {
        val request = getRequest().copy(description = description)
        val result = HibernateValidator.default.validate(request)

        assertThat(result).hasSize(1)
        assertThat(result.first().message).isEqualTo("플레이리스트 설명은 1자 이상 ${Playlist.MAX_DESCRIPTION_LENGTH}자 이하이어야 합니다.")
    }

    @Test
    fun invalidTracks() {
        val request = getRequest().copy(
            tracks =
                List(Playlist.MAX_TRACK_COUNT + 1) {
                    PlaylistCreationRequestTrack(
                        embedId = "embedId",
                        title = "title",
                        startTimeSec = 0,
                        endTimeSec = 100,
                        repeatCount = 1,
                        additionalTitles = emptySet(),
                        isRepresentative = it == 0,
                    )
                }
        )
        val result = HibernateValidator.default.validate(request)
        assertThat(result).hasSize(1)
        assertThat(result.first().message).isEqualTo("플레이리스트에는 최소 1개, 최대 ${Playlist.MAX_TRACK_COUNT}개의 곡이 포함되어야 합니다.")
    }

    @Test
    fun emptyTracks() {
        assertThatThrownBy {
            getRequest().copy(
                tracks = emptyList()
            )
        }.isExactlyInstanceOf(BadRequestException::class.java)
            .hasMessage("대표 곡은 반드시 1개여야 합니다.")
    }

    @Test
    fun `isRepresentative_대표 곡이 여러 개인 경우`() {
        assertThatThrownBy {
            getRequest().copy(
                tracks = listOf(
                    PlaylistCreationRequestTrack(
                        embedId = "embedId",
                        title = "title",
                        startTimeSec = 0,
                        endTimeSec = 100,
                        repeatCount = 1,
                        additionalTitles = emptySet(),
                        isRepresentative = true,
                    ),
                    PlaylistCreationRequestTrack(
                        embedId = "embedId2",
                        title = "title2",
                        startTimeSec = 0,
                        endTimeSec = 100,
                        repeatCount = 1,
                        additionalTitles = emptySet(),
                        isRepresentative = true,
                    )
                )
            )
        }.isExactlyInstanceOf(BadRequestException::class.java)
            .hasMessage("대표 곡은 반드시 1개여야 합니다.")
    }

    @ParameterizedTest
    @MethodSource("invalidEmbedId")
    fun invalidEmbedId(embedId: String) {
        val request = getRequest().copy(
            tracks = listOf(
                PlaylistCreationRequestTrack(
                    embedId = embedId,
                    title = "title",
                    startTimeSec = 0,
                    endTimeSec = 100,
                    repeatCount = 1,
                    additionalTitles = emptySet(),
                    isRepresentative = true,
                )
            )
        )
        val result = HibernateValidator.default.validate(request)

        assertThat(result).hasSize(1)
        assertThat(result.first().message).isEqualTo("embedId는 1자 이상 ${Track.MAX_EMBED_ID_LENGTH}자 이하이어야 합니다.")
    }

    @ParameterizedTest
    @MethodSource("invalidTrackTitle")
    fun invalidTrackTitle(title: String) {
        val request = getRequest().copy(
            tracks = listOf(
                PlaylistCreationRequestTrack(
                    embedId = "embedId",
                    title = title,
                    startTimeSec = 0,
                    endTimeSec = 100,
                    repeatCount = 1,
                    additionalTitles = emptySet(),
                    isRepresentative = true,
                )
            )
        )
        val result = HibernateValidator.default.validate(request)

        assertThat(result).hasSize(1)
        assertThat(result.first().message).isEqualTo("곡 제목은 1자 이상 ${Track.MAX_TITLE_LENGTH}자 이하이어야 합니다.")
    }

    @ParameterizedTest
    @ValueSource(ints = [0, Track.MAX_REPEAT_COUNT + 1])
    fun invalidRepeatCount(repeatCount: Int) {
        val request = getRequest().copy(
            tracks = listOf(
                PlaylistCreationRequestTrack(
                    embedId = "embedId",
                    title = "title",
                    startTimeSec = 0,
                    endTimeSec = 100,
                    repeatCount = repeatCount,
                    additionalTitles = emptySet(),
                    isRepresentative = true,
                )
            )
        )
        val result = HibernateValidator.default.validate(request)

        assertThat(result).hasSize(1)
        assertThat(result.first().propertyPath.toString()).isEqualTo("tracks[0].repeatCount")
    }

    @Test
    fun `endTimeSec보다 startTimeSec이 큰 경우`() {
        assertThatThrownBy {
            getRequest().copy(
                tracks = listOf(
                    PlaylistCreationRequestTrack(
                        embedId = "embedId",
                        title = "title",
                        startTimeSec = 10,
                        endTimeSec = 5,
                        repeatCount = 1,
                        additionalTitles = emptySet(),
                        isRepresentative = true,
                    )
                )
            )
        }.isExactlyInstanceOf(BadRequestException::class.java)
            .hasMessage("곡 시작 시각은 곡 종료 시각보다 클 수 없습니다.")
    }

    @Test
    fun `additionalTitles_추가 제목이 너무 많은 경우`() {
        val request = getRequest().copy(
            tracks = listOf(
                PlaylistCreationRequestTrack(
                    embedId = "embedId",
                    title = "title",
                    startTimeSec = 0,
                    endTimeSec = 100,
                    repeatCount = 1,
                    additionalTitles = List(Track.MAX_ADDITIONAL_TITLE_COUNT + 1) { "additionalTitle$it" }.toSet(),
                    isRepresentative = true,
                )
            )
        )
        val result = HibernateValidator.default.validate(request)

        assertThat(result).hasSize(1)
        assertThat(result.first().message).isEqualTo("추가 정답은 최대 ${Track.MAX_ADDITIONAL_TITLE_COUNT}개까지 입력할 수 있습니다.")
    }

    @Test
    fun `additionalTitles_추가 제목이 빈 문자열인 경우`() {
        assertThatThrownBy {
            getRequest().copy(
                tracks = listOf(
                    PlaylistCreationRequestTrack(
                        embedId = "embedId",
                        title = "title",
                        startTimeSec = 0,
                        endTimeSec = 100,
                        repeatCount = 1,
                        additionalTitles = setOf(""),
                        isRepresentative = true,
                    )
                )
            )
        }.isExactlyInstanceOf(BadRequestException::class.java)
            .hasMessage("추가 정답은 1자 이상 ${Track.MAX_TITLE_LENGTH}자 이하이어야 합니다.")
    }

    private fun getRequest(): PlaylistCreationRequest {
        return PlaylistCreationRequest(
            title = "title",
            description = "description",
            tracks = listOf(
                PlaylistCreationRequestTrack(
                    embedId = "embedId",
                    title = "title",
                    startTimeSec = 0,
                    endTimeSec = 100,
                    repeatCount = 1,
                    additionalTitles = setOf("additionalTitle1-1", "additionalTitle1-2"),
                    isRepresentative = true,
                ),
                PlaylistCreationRequestTrack(
                    embedId = "embedId2",
                    title = "title2",
                    startTimeSec = 0,
                    endTimeSec = 100,
                    repeatCount = 1,
                    additionalTitles = setOf("additionalTitle2-1", "additionalTitle2-2"),
                    isRepresentative = false,
                )
            )
        )
    }

    companion object {
        @JvmStatic
        fun invalidTitle(): List<String> = listOf(
            "",
            "a".repeat(Playlist.MAX_TITLE_LENGTH + 1)
        )

        @JvmStatic
        fun invalidDescription(): List<String> = listOf(
            "",
            "a".repeat(Playlist.MAX_DESCRIPTION_LENGTH + 1)
        )

        @JvmStatic
        fun invalidEmbedId(): List<String> = listOf(
            "",
            "a".repeat(Track.MAX_EMBED_ID_LENGTH + 1)
        )

        @JvmStatic
        fun invalidTrackTitle(): List<String> = listOf(
            "",
            "a".repeat(Track.MAX_TITLE_LENGTH + 1)
        )
    }
}
