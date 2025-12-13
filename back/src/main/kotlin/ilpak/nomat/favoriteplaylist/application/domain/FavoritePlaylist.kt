package ilpak.nomat.favoriteplaylist.application.domain

import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.io.Serializable
import java.time.LocalDateTime

@Entity
@EntityListeners(AuditingEntityListener::class)
class FavoritePlaylist(
    @EmbeddedId
    val id: FavoritePlaylistId,
    @CreatedDate
    var createdDate: LocalDateTime? = null,
) {
    val playerId: Long
        get() = id.playerId
    val playlistId: Long
        get() = id.playlistId

    companion object {
        const val MAX_FAVORITE_PLAYLISTS = 1000
    }
}

@Embeddable
data class FavoritePlaylistId(
    val playerId: Long,
    val playlistId: Long,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
