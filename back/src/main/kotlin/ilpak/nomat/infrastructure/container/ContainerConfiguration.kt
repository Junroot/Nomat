package ilpak.nomat.infrastructure.container

import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.kafka.KafkaContainer

@Configuration(proxyBeanMethods = false)
@Profile(value = ["local", "test"])
class ContainerConfiguration {

    @Bean
    @ServiceConnection
    fun mySQLContainer(): MySQLContainer<*> {
        return MySQLContainer("mysql:8.0.39")
    }

    @Bean
    @ServiceConnection
    fun elasticsearchContainer(): ElasticsearchContainer {
        return ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:9.0.4")
    }

    @Bean
    @ServiceConnection
    fun kafkaContainer(): KafkaContainer {
        return KafkaContainer("apache/kafka:3.7.2")
    }
}
