package ilpak.nomat.playlist.application.dto

import ilpak.nomat.player.application.dto.PlayerResponse
import ilpak.nomat.playlist.application.domain.Playlist
import ilpak.nomat.playlist.application.domain.Track

data class PlaylistWithTrackResponse(
    val id: Long,
    val title: String,
    val description: String,
    val master: PlaylistWithTrackResponseMaster,
    val tracks: List<PlaylistWithTrackTrackResponse>
) {
    companion object {
        fun of(playlist: Playlist, tracks: List<Track>, master: PlayerResponse): PlaylistWithTrackResponse {
            return PlaylistWithTrackResponse(
                playlist.id,
                playlist.title,
                playlist.description,
                PlaylistWithTrackResponseMaster.of(master),
                tracks.map { PlaylistWithTrackTrackResponse.of(it) }
            )
        }
    }
}

data class PlaylistWithTrackResponseMaster(
    val id: Long,
    val nickname: String,
) {
    companion object {
        fun of(master: PlayerResponse): PlaylistWithTrackResponseMaster {
            return PlaylistWithTrackResponseMaster(
                master.id,
                master.nickname
            )
        }
    }
}

data class PlaylistWithTrackTrackResponse(
    val embedId: String,
    val title: String,
    val startTimeSec: Int,
    val endTimeSec: Int,
    val repeatCount: Int,
    val additionalTitles: Set<String>,
    val isRepresentative: Boolean,
) {
    companion object {
        fun of(track: Track): PlaylistWithTrackTrackResponse {
            return PlaylistWithTrackTrackResponse(
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
