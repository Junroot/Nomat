package ilpak.nomat.infrastructure.integration

import org.flywaydb.core.Flyway
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.TestContext
import org.springframework.test.context.support.AbstractTestExecutionListener

class IntegrationTestExecutionListener : AbstractTestExecutionListener() {

    override fun prepareTestInstance(testContext: TestContext) {
        val applicationContext = testContext.applicationContext

        val flyway = applicationContext.getBean(Flyway::class.java)
        flyway.clean()
        flyway.migrate()

        val redisTemplate = applicationContext.getBean(StringRedisTemplate::class.java)
        redisTemplate.connectionFactory?.connection?.use { it.serverCommands().flushAll() }
    }
}
