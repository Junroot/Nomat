package ilpak.nomat.playlist.application.dto

import ilpak.nomat.player.application.dto.PlayerResponse
import ilpak.nomat.playlist.application.domain.Playlist

data class PlaylistMetaDataResponse(
    val id: Long,
    val title: String,
    val master: PlaylistMetaDataResponseMaster,
    val description: String
) {
    companion object {
        fun of(playlist: Playlist, master: PlayerResponse): PlaylistMetaDataResponse {
            return PlaylistMetaDataResponse(
                playlist.id,
                playlist.title,
                PlaylistMetaDataResponseMaster.of(master),
                playlist.description
            )
        }
    }
}

data class PlaylistMetaDataResponseMaster(
    val id: Long,
    val nickname: String,
) {
    companion object {
        fun of(master: PlayerResponse): PlaylistMetaDataResponseMaster {
            return PlaylistMetaDataResponseMaster(
                master.id,
                master.nickname
            )
        }
    }
}
