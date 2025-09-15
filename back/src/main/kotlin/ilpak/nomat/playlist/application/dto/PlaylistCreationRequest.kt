package ilpak.nomat.playlist.application.dto

import ilpak.nomat.infrastructure.exception.BadRequestException
import ilpak.nomat.playlist.application.domain.Playlist
import ilpak.nomat.playlist.application.domain.Track
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.Length

data class PlaylistCreationRequest(
    @field:Length(min = 1, max = Playlist.MAX_TITLE_LENGTH, message = "플레이리스트 제목은 {min}자 이상 {max}자 이하이어야 합니다.")
    val title: String,
    @field:Length(min = 1, max = Playlist.MAX_DESCRIPTION_LENGTH, message = "플레이리스트 설명은 {min}자 이상 {max}자 이하이어야 합니다.")
    val description: String,
    @field:Size(min = 1, max = Playlist.MAX_TRACK_COUNT, message = "플레이리스트에는 최소 {min}개, 최대 {max}개의 곡이 포함되어야 합니다.")
    @field:Valid
    val tracks: List<PlaylistCreationRequestTrack>,
) {
    init {
        val representativeCount = tracks.count { it.isRepresentative }
        if (representativeCount != 1) {
            throw BadRequestException("대표 곡은 반드시 1개여야 합니다.")
        }
    }

    fun toDomain(): Playlist {
        return Playlist(
            title = title,
            description = description,
        )
    }
}

data class PlaylistCreationRequestTrack(
    @field:Length(min = 1, max = Track.MAX_EMBED_ID_LENGTH, message = "embedId는 {min}자 이상 {max}자 이하이어야 합니다.")
    val embedId: String,
    @field:Length(min = 1, max = Track.MAX_TITLE_LENGTH, message = "곡 제목은 {min}자 이상 {max}자 이하이어야 합니다.")
    val title: String,
    @field:Min(0, message = "곡 시작 시각은 0초 이상이어야 합니다.")
    val startTimeSec: Int,
    @field:Min(0, message = "곡 종료 시각은 0초 이상이어야 합니다.")
    val endTimeSec: Int,
    @field:Min(1, message = "반복 횟수는 최소 {value}회 이상이어야 합니다.")
    @field:Max(Track.MAX_REPEAT_COUNT.toLong(), message = "반복 횟수는 최대 {value}회 이하이어야 합니다.")
    val repeatCount: Int,
    @field:Size(min = 0, max = Track.MAX_ADDITIONAL_TITLE_COUNT, message = "추가 정답은 최대 {max}개까지 입력할 수 있습니다.")
    val additionalTitles: Set<String>,
    val isRepresentative: Boolean,
) {
    init {
        if (startTimeSec > endTimeSec) {
            throw BadRequestException("곡 시작 시각은 곡 종료 시각보다 클 수 없습니다.")
        }

        additionalTitles.forEach {
            if (it.isEmpty() || it.length > Track.MAX_TITLE_LENGTH) {
                throw BadRequestException("추가 정답은 1자 이상 ${Track.MAX_TITLE_LENGTH}자 이하이어야 합니다.")
            }
        }
    }

    fun toDomain(playlist: Playlist): Track {
        return Track(
            embedId = embedId,
            title = title,
            startTimeSec = startTimeSec,
            endTimeSec = endTimeSec,
            repeatCount = repeatCount,
            additionalTitles = additionalTitles,
            playlist = playlist,
            isRepresentative = isRepresentative,
        )
    }
}
