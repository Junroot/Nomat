package ilpak.nomat.infrastructure.container

import com.redis.testcontainers.RedisContainer
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchConnectionDetails
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.utility.DockerImageName

@Configuration(proxyBeanMethods = false)
@Profile(value = ["local", "test"])
class ContainerConfiguration {

    @Bean
    @ServiceConnection
    fun mySQLContainer(): MySQLContainer<*> {
        return MySQLContainer("mysql:8.0.39")
            .withDatabaseName("nomat")
            .withCommand("mysqld", "--binlog-rows-query-log-events=ON")
            .withUsername("root")
            .withPassword("root")
    }

    @Bean
    @ServiceConnection
    fun elasticsearchContainer(): ElasticsearchContainer {
        val image = DockerImageName.parse("junroot0909/elasticsearch-nori:9.0.4")
            .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch")

        return ElasticsearchContainer(image)
            .withEnv("xpack.security.enabled", "false")
            .withEnv("xpack.security.http.ssl.enabled", "false")
            .withPassword(ELASTICSEARCH_PASSWORD)
    }

    @Bean
    fun elasticsearchConnectionDetails(elasticsearchContainer: ElasticsearchContainer): ElasticsearchConnectionDetails {
        return object : ElasticsearchConnectionDetails {
            override fun getNodes(): List<ElasticsearchConnectionDetails.Node> {
                val host: String = elasticsearchContainer.host
                val port: Int = elasticsearchContainer.getMappedPort(ELASTICSEARCH_DEFAULT_PORT)
                return listOf(
                    ElasticsearchConnectionDetails.Node(
                        host,
                        port,
                        ElasticsearchConnectionDetails.Node.Protocol.HTTP,
                        null,
                        null
                    )
                )
            }

            override fun getUsername(): String = "elastic"
            override fun getPassword(): String = ELASTICSEARCH_PASSWORD
        }
    }

    @Bean
    @ServiceConnection
    fun redisContainer(): RedisContainer {
        return RedisContainer(DockerImageName.parse("redis:7.4.7"))
    }

    companion object {
        const val ELASTICSEARCH_DEFAULT_PORT = 9200
        const val ELASTICSEARCH_PASSWORD = "passw0rd"
    }
}
