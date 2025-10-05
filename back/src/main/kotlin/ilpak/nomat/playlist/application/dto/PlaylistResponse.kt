package ilpak.nomat.playlist.application.dto

import ilpak.nomat.player.application.dto.PlayerResponse
import ilpak.nomat.playlist.application.domain.Playlist
import ilpak.nomat.playlist.application.domain.Track

data class PlaylistResponse(
    val id: Long,
    val title: String,
    val description: String,
    val master: PlaylistResponseMaster,
    val trackCount: Int,
    val expectedPlayTimeSec: Long,
    val favorite: Boolean,
    val representativeTrack: PlaylistTrackResponse,
) {
    companion object {
        fun of(playlist: Playlist, tracks: List<Track>, master: PlayerResponse, favorite: Boolean): PlaylistResponse {
            return PlaylistResponse(
                playlist.id,
                playlist.title,
                playlist.description,
                PlaylistResponseMaster.of(master),
                tracks.size,
                tracks.sumOf { (it.endTimeSec - it.startTimeSec).toLong() * it.repeatCount },
                favorite,
                tracks.firstOrNull { it.representative }?.let { PlaylistTrackResponse.of(it) }
                    ?: PlaylistTrackResponse.of(tracks.first()),
            )
        }
    }
}

data class PlaylistResponseMaster(
    val id: Long,
    val nickname: String,
    val displayName: String,
) {
    companion object {
        fun of(master: PlayerResponse): PlaylistResponseMaster {
            return PlaylistResponseMaster(
                master.id,
                master.nickname,
                master.displayName,
            )
        }
    }
}

data class PlaylistTrackResponse(
    val embedId: String,
    val startTimeSec: Int,
    val endTimeSec: Int,
) {
    companion object {
        fun of(track: Track): PlaylistTrackResponse {
            return PlaylistTrackResponse(
                track.embedId,
                track.startTimeSec,
                track.endTimeSec,
            )
        }
    }
}
