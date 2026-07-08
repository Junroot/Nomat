package ilpak.nomat.infrastructure.integration

import ilpak.nomat.playlist.out.document.PlaylistDocument
import org.awaitility.Awaitility.await
import org.flywaydb.core.Flyway
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.query.DeleteQuery
import org.springframework.data.elasticsearch.core.query.Query
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestContext
import org.springframework.test.context.support.AbstractTestExecutionListener
import java.time.Duration

class IntegrationTestExecutionListener : AbstractTestExecutionListener() {

    override fun prepareTestInstance(testContext: TestContext) {
        val applicationContext = testContext.applicationContext

        // 이전 테스트가 발행한 비동기 ES 동기화 이벤트(EsPlaylistSyncHandler)가 모두 처리될 때까지 대기한다.
        // 기다리지 않고 아래 ES 정리를 실행하면 진행 중인 비동기 ES write와 _delete_by_query가
        // 같은 문서를 동시에 건드려 seqNo 낙관적 락 충돌(409 version conflict)이 발생한다.
        // Modulith outbox(event_publication)에서 미완료(completion_date IS NULL) 항목이 0이 되면
        // 모든 비동기 핸들러가 끝났다는 의미다.
        val jdbcTemplate = applicationContext.getBean(JdbcTemplate::class.java)
        await()
            .atMost(Duration.ofSeconds(10))
            .pollDelay(Duration.ZERO)
            .pollInterval(Duration.ofMillis(50))
            .until { countIncompletePublications(jdbcTemplate) == 0L }

        val flyway = applicationContext.getBean(Flyway::class.java)
        flyway.clean()
        flyway.migrate()

        val redisTemplate = applicationContext.getBean(StringRedisTemplate::class.java)
        redisTemplate.connectionFactory?.connection?.use { it.serverCommands().flushAll() }

        val elasticsearchOperations = applicationContext.getBean(ElasticsearchOperations::class.java)
        val indexOperations = elasticsearchOperations.indexOps(PlaylistDocument::class.java)

        // delete_by_query는 내부 검색으로 대상 문서를 찾고, 그때 검색 가능(마지막 refresh) 상태의
        // seqNo를 낙관적 락 조건으로 삭제한다. operations.save()는 즉시 refresh하지 않으므로
        // 검색 가능 seqNo가 ack된 실제 seqNo보다 뒤처지고, 이 경우 삭제 조건이 어긋나
        // 409 version conflict가 난다. 삭제 전에 refresh해 모든 ack된 write를 검색 가능 상태로
        // 맞추면(위 대기로 동시 write는 이미 배제됨) delete가 현재 seqNo를 정확히 보고 삭제한다.
        indexOperations.refresh()
        elasticsearchOperations.delete(DeleteQuery.builder(Query.findAll()).build(), PlaylistDocument::class.java)
        indexOperations.refresh()
    }

    private fun countIncompletePublications(jdbcTemplate: JdbcTemplate): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM event_publication WHERE completion_date IS NULL",
            Long::class.java,
        ) ?: 0L
}
