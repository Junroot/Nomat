package ilpak.nomat.playlist.`in`

import ilpak.nomat.playlist.application.domain.PlaylistDeleted
import ilpak.nomat.playlist.application.domain.PlaylistUpserted
import ilpak.nomat.playlist.out.document.PlaylistDocument
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

@Component
private class EsPlaylistSyncHandler(
    private val operations: ElasticsearchOperations,
) {

    @ApplicationModuleListener(id = "es-sync-playlist-upserted", readOnlyTransaction = true)
    fun handleUpserted(event: PlaylistUpserted) {
        operations.save(
            PlaylistDocument(
                title = event.title,
                description = event.description,
                id = event.id,
            )
        )
    }

    @ApplicationModuleListener(id = "es-sync-playlist-deleted", readOnlyTransaction = true)
    fun handleDeleted(event: PlaylistDeleted) {
        operations.delete(event.playlistId.toString(), PlaylistDocument::class.java)
    }
}
