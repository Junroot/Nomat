package ilpak.nomat.infrastructure.events

import ilpak.nomat.infrastructure.events.fixture.FailingTestEvent
import ilpak.nomat.infrastructure.events.fixture.FailingTestHandler
import ilpak.nomat.infrastructure.integration.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.modulith.events.IncompleteEventPublications
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration

@IntegrationTest
class EventPublicationRegistryTest(
    @Autowired private val handler: FailingTestHandler,
    @Autowired private val incompleteEventPublications: IncompleteEventPublications,
    @Autowired private val transactionTemplate: TransactionTemplate,
    @Autowired private val publisher: ApplicationEventPublisher,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {

    @BeforeEach
    fun resetHandler() {
        handler.reset()
    }

    @Test
    fun `핸들러 실패 시 publication은 미완료로 남고 재시도가 재처리`() {
        transactionTemplate.executeWithoutResult {
            publisher.publishEvent(FailingTestEvent("event-1"))
        }

        await()
            .atMost(Duration.ofSeconds(SYNC_TIMEOUT_SECONDS))
            .untilAsserted {
                assertThat(handler.attemptCount.get()).isGreaterThanOrEqualTo(1)
            }

        val incompleteCount = jdbcTemplate.queryForObject(INCOMPLETE_COUNT_SQL, Long::class.java)
        assertThat(incompleteCount).isGreaterThanOrEqualTo(1)

        handler.shouldFail = false
        incompleteEventPublications.resubmitIncompletePublications { _ -> true }

        await()
            .atMost(Duration.ofSeconds(SYNC_TIMEOUT_SECONDS))
            .untilAsserted {
                assertThat(handler.attemptCount.get()).isGreaterThanOrEqualTo(2)
                val remaining = jdbcTemplate.queryForObject(INCOMPLETE_COUNT_SQL, Long::class.java)
                assertThat(remaining).isEqualTo(0L)
            }
    }

    @Test
    fun `핸들러 정상 완료 시 publication row 즉시 삭제`() {
        handler.shouldFail = false

        transactionTemplate.executeWithoutResult {
            publisher.publishEvent(FailingTestEvent("event-success"))
        }

        await()
            .atMost(Duration.ofSeconds(SYNC_TIMEOUT_SECONDS))
            .untilAsserted {
                assertThat(handler.attemptCount.get()).isGreaterThanOrEqualTo(1)
                val total = jdbcTemplate.queryForObject(TOTAL_COUNT_SQL, Long::class.java)
                assertThat(total).isEqualTo(0L)
            }
    }

    companion object {
        private const val SYNC_TIMEOUT_SECONDS = 10L
        private const val INCOMPLETE_COUNT_SQL =
            "SELECT COUNT(*) FROM event_publication WHERE completion_date IS NULL"
        private const val TOTAL_COUNT_SQL =
            "SELECT COUNT(*) FROM event_publication"
    }
}
