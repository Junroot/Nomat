package ilpak.nomat.playlist.application.dto

import ilpak.nomat.player.application.dto.PlayerResponse
import ilpak.nomat.playlist.application.domain.Playlist
import ilpak.nomat.playlist.application.domain.Track

data class PlaylistResponse(
    val id: Long,
    val title: String,
    val description: String,
    val master: PlaylistResponseMaster,
    val tracks: List<PlaylistTrackResponse>
) {
    companion object {
        fun of(playlist: Playlist, tracks: List<Track>, master: PlayerResponse): PlaylistResponse {
            return PlaylistResponse(
                playlist.id,
                playlist.title,
                playlist.description,
                PlaylistResponseMaster.of(master),
                tracks.map { PlaylistTrackResponse.of(it) }
            )
        }
    }
}

data class PlaylistResponseMaster(
    val id: Long,
    val nickname: String,
) {
    companion object {
        fun of(master: PlayerResponse): PlaylistResponseMaster {
            return PlaylistResponseMaster(
                master.id,
                master.nickname
            )
        }
    }
}

data class PlaylistTrackResponse(
    val embedId: String,
    val title: String,
    val startTimeSec: Int,
    val endTimeSec: Int,
    val repeatCount: Int,
    val additionalTitles: Set<String>,
    val isRepresentative: Boolean,
) {
    companion object {
        fun of(track: Track): PlaylistTrackResponse {
            return PlaylistTrackResponse(
                track.embedId,
                track.title,
                track.startTimeSec,
                track.endTimeSec,
                track.repeatCount,
                track.additionalTitles,
                track.representative,
            )
        }
    }
}
