package ilpak.nomat.playlist.application.dto

import ilpak.nomat.player.application.domain.RegistrationType
import ilpak.nomat.player.application.dto.PlayerResponse
import ilpak.nomat.playlist.application.domain.Playlist
import ilpak.nomat.playlist.application.domain.Track

data class PlaylistMetaDataResponse(
    val id: Long,
    val title: String,
    val representativeTrack: PlaylistMetaDataResponseTrack,
    val master: PlaylistMetaDataResponseMaster,
    val description: String
) {
    companion object {
        fun of(playlist: Playlist, representativeTrack: Track, master: PlayerResponse): PlaylistMetaDataResponse {
            return PlaylistMetaDataResponse(
                playlist.id,
                playlist.title,
                PlaylistMetaDataResponseTrack.of(representativeTrack),
                PlaylistMetaDataResponseMaster.of(master),
                playlist.description
            )
        }
    }
}

data class PlaylistMetaDataResponseTrack(
    val embedId: String,
    val title: String,
) {
    companion object {
        fun of(track: Track): PlaylistMetaDataResponseTrack {
            return PlaylistMetaDataResponseTrack(
                track.embedId,
                track.title,
            )
        }
    }
}

data class PlaylistMetaDataResponseMaster(
    val id: Long,
    val nickname: String,
    val registrationType: RegistrationType,
    val displayName: String,
) {
    companion object {
        fun of(master: PlayerResponse): PlaylistMetaDataResponseMaster {
            return PlaylistMetaDataResponseMaster(
                master.id,
                master.nickname,
                master.registrationType,
                master.displayName,
            )
        }
    }
}
