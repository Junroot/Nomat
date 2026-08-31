package ilpak.nomat.playlist.application.dto

import ilpak.nomat.common.exception.BadRequestException
import ilpak.nomat.common.normalize.TitleNormalizer
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
    // `Set`이 아니라 `List`인 이유 — Jackson 은 `Set<String>`을 순서 없는 `HashSet`으로 역직렬화해
    // 입력 순서를 지운다. [foldedAdditionalTitles]가 "먼저 온 값을 남긴다"를 지키려면 순서가 필요하다.
    val additionalTitles: List<String>,
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
            additionalTitles = foldedAdditionalTitles(),
            playlist = playlist,
            representative = isRepresentative,
        )
    }

    /**
     * 표기 정규화 키가 같은 추가 정답을 하나로 접는다.
     *
     * 매칭상 구분 불가한 두 값은 플레이어가 무엇을 입력하든 같은 판정을 내므로, 둘 다 보관하면
     * 추가 정답 예산(최대 [Track.MAX_ADDITIONAL_TITLE_COUNT]개)만 잠식한다.
     *
     * 거부하지 않고 **조용히 접는다** — 이 규칙 도입 이전에 저장된 중복을 가진 플레이리스트가
     * 400 때문에 편집 불가 상태로 잠기는 것을 막기 위해서다. 프론트는 새로 추가하는 항목만
     * 사전 차단할 수 있고 이미 목록에 있는 항목은 막지 못한다.
     *
     * 남기는 쪽은 **먼저 등록된 값**이다. 사용자가 처음 입력한 표기를 유지하는 편이 기대에 가깝다.
     * 저장되는 값은 정규화 키가 아니라 입력 원문 그대로다 — 편집 화면에 그대로 내려가기 때문이다.
     */
    private fun foldedAdditionalTitles(): Set<String> =
        additionalTitles.distinctBy(TitleNormalizer::normalize).toSet()
}
