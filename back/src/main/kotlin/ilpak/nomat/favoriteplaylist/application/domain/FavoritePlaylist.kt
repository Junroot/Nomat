package ilpak.nomat.favoriteplaylist.application.domain

import ilpak.nomat.common.AuditDateMetadata
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import org.hibernate.annotations.SQLDelete
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.io.Serializable
import java.time.LocalDateTime

@Entity
@EntityListeners(AuditingEntityListener::class)
@SQLDelete(sql = "UPDATE favorite_playlist SET deleted_date = CURRENT_TIMESTAMP WHERE player_id = ? AND playlist_id = ?")
class FavoritePlaylist(
    @EmbeddedId
    val id: FavoritePlaylistId,
    @Embedded
    val auditDateMetadata: AuditDateMetadata = AuditDateMetadata(),
    var deletedDate: LocalDateTime? = null,
) {
    val playerId: Long
        get() = id.playerId
    val playlistId: Long
        get() = id.playlistId

    fun restore() {
        deletedDate = null
    }

    companion object {
        const val MAX_FAVORITE_PLAYLISTS = 1000
    }
}

@Embeddable
data class FavoritePlaylistId(
    val playerId: Long,
    val playlistId: Long,
) : Serializable
