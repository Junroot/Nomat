package ilpak.nomat.playlist.dto

data class PlaylistMetaDataResponse(
    val id: Long,
    val name: String,
    val count: Int,
    val master: String,
    val comment: String
)
