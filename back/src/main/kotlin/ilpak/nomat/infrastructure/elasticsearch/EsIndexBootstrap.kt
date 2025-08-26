package ilpak.nomat.infrastructure.elasticsearch

import ilpak.nomat.playlist.out.document.PlaylistDocument
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.stereotype.Component

@Component
class EsIndexBootstrap(
    private val operations: ElasticsearchOperations
): ApplicationRunner {
    override fun run(args: ApplicationArguments?) {
        val indexOps = operations.indexOps(PlaylistDocument::class.java)

        if (!indexOps.exists()) {
            indexOps.create()
            indexOps.putMapping(indexOps.createMapping())
            indexOps.refresh()
        }
    }
}
