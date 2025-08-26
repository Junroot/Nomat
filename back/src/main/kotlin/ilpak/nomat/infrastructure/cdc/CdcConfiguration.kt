package ilpak.nomat.infrastructure.cdc


import io.debezium.connector.mysql.MySqlConnector
import io.debezium.engine.spi.OffsetCommitPolicy.PeriodicCommitOffsetPolicy
import org.apache.kafka.connect.storage.KafkaOffsetBackingStore
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.URI
import kotlin.random.Random

@Configuration
class CdcConfiguration {

    @Bean
    fun debeziumConfig(
        jdbcConnectionDetails: JdbcConnectionDetails,
        kafkaConnectionDetails: KafkaConnectionDetails,
    ): io.debezium.config.Configuration {
        val databaseUri = URI(jdbcConnectionDetails.jdbcUrl.removePrefix("jdbc:"))
        val bootstrapServers = kafkaConnectionDetails.bootstrapServers.joinToString(",")

        return io.debezium.config.Configuration.create()
            .with("name", "nomat-mysql-connector")
            .with("connector.class", MySqlConnector::class.java)
            .with("bootstrap.servers", bootstrapServers)
            .with("offset.storage", KafkaOffsetBackingStore::class.java)
            .with("offset.storage.topic", "nomat_mysql_offset")
            .with("offset.storage.partitions", 1)
            .with("offset.storage.replication.factor", 1)
            .with("offset.commit.policy", PeriodicCommitOffsetPolicy::class.java)
            .with("offset.flush.interval.ms", 60000)
            .with("offset.flush.timeout.ms", 5000)
            .with("errors.max.retries", -1)
            .with("errors.retry.delay.initial.ms", 300)
            .with("errors.retry.delay.max.ms", 10000)
            .with("database.hostname", databaseUri.host)
            .with("database.port", databaseUri.port)
            .with("database.user", jdbcConnectionDetails.username)
            .with("database.password", jdbcConnectionDetails.password)
            .with("database.include.list", "nomat")
            .with("database.server.id", Random.nextLong(Long.MAX_VALUE))
            .with("table.include.list", "nomat.playlist")
            .with("topic.prefix", "mysql_playlist")
            .with("include.schema.changes", false)
            .with("schema.history.internal.kafka.topic", "nomat_mysql_schema_history")
            .with("schema.history.internal.kafka.bootstrap.servers", bootstrapServers)
            .build()
    }
}
