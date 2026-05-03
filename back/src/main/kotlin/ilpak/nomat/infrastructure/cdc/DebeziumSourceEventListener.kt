package ilpak.nomat.infrastructure.cdc

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.treeToValue
import ilpak.nomat.playlist.out.document.PlaylistDocument
import io.debezium.config.Configuration
import io.debezium.data.Envelope
import io.debezium.engine.ChangeEvent
import io.debezium.engine.DebeziumEngine
import io.debezium.engine.format.Json
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.stereotype.Component
import java.util.concurrent.Executors

private val logger = KotlinLogging.logger {}

@Component
class DebeziumSourceEventListener(
    configuration: Configuration,
    private val operations: ElasticsearchOperations,
) {
    private val objectMapper = ObjectMapper()
        .findAndRegisterModules()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
    private val executor = Executors.newSingleThreadExecutor()
    private val debeziumEngine = DebeziumEngine.create(Json::class.java)
        .using(configuration.asProperties())
        .notifying(::handleChangeEvent)
        .build()

    @PostConstruct
    fun start() {
        executor.execute(debeziumEngine)
    }

    @PreDestroy
    fun stop() {
        debeziumEngine.close()
        executor.shutdown()
    }

    fun handleChangeEvent(changeEvent: ChangeEvent<String, String>) {
        try {
            if (changeEvent.value() == null) {
                val key = objectMapper.readTree(changeEvent.key())
                val id = key.get("payload")?.get("id")?.asLong()

                if (id != null) {
                    delete(id)
                }
                return
            }

            val value = objectMapper.readTree(changeEvent.value())
            val payload = checkNotNull(value.get("payload")) { "No payload found" }
            val op = checkNotNull(payload.get(Envelope.FieldName.OPERATION)) { "No operation found" }
            val operation =
                checkNotNull(Envelope.Operation.forCode(op.asText())) { "Unknown operation: ${op.asText()}" }

            when (operation) {
                Envelope.Operation.CREATE, Envelope.Operation.UPDATE -> upsert(payload)
                Envelope.Operation.DELETE -> delete(payload)
                Envelope.Operation.TRUNCATE -> truncate()
                Envelope.Operation.READ, Envelope.Operation.MESSAGE -> {}
            }
        } catch (e: Exception) {
            logger.error(e) { "CDC 이벤트 처리 중 오류 발생: ${changeEvent.key()}" }
        }
    }

    private fun upsert(payload: JsonNode) {
        val after = checkNotNull(payload.get(Envelope.FieldName.AFTER)) { "No after found" }
        val document = objectMapper.treeToValue<PlaylistDocument>(after)

        operations.save(document)
    }

    private fun delete(payload: JsonNode) {
        val before = checkNotNull(payload.get(Envelope.FieldName.BEFORE)) { "No before found" }
        val document = objectMapper.treeToValue<PlaylistDocument>(before)

        delete(document.id)
    }

    private fun delete(id: Long) {
        operations.delete(id.toString(), PlaylistDocument::class.java)
    }

    private fun truncate() {
        val indexOps = operations.indexOps(PlaylistDocument::class.java)
        indexOps.delete()
        indexOps.create()
        indexOps.refresh()
    }
}
