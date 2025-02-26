package ilpak.nomat.playlist.application.dto

data class PlaylistMetaDataResponse(
    val id: Long,
    val name: String,
    val count: Int,
    val masterId: Long,
    val comment: String
)
