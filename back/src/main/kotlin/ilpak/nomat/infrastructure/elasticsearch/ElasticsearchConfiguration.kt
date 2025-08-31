package ilpak.nomat.infrastructure.elasticsearch

import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchConnectionDetails
import org.springframework.context.annotation.Configuration
import org.springframework.data.elasticsearch.client.ClientConfiguration
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories

@Configuration
@EnableElasticsearchRepositories("ilpak.nomat")
class ElasticsearchConfiguration(private val elasticsearchConnectionDetails: ElasticsearchConnectionDetails) :
    ElasticsearchConfiguration() {

    override fun clientConfiguration(): ClientConfiguration {
        val hostsAndPorts = elasticsearchConnectionDetails.nodes.map {
            "${it.hostname}:${it.port}"
        }.toTypedArray()

        return ClientConfiguration.builder()
            .connectedTo(*hostsAndPorts)
            .withBasicAuth(elasticsearchConnectionDetails.username, elasticsearchConnectionDetails.password)
            .build()
    }
}
