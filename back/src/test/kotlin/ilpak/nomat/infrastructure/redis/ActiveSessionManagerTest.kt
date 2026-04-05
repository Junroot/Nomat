package ilpak.nomat.infrastructure.redis

import ilpak.nomat.infrastructure.integration.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@IntegrationTest
class ActiveSessionManagerTest(
    @Autowired private val activeSessionManager: ActiveSessionManager,
) {

    @Test
    fun `setSession_세션을 등록하고 조회한다`() {
        activeSessionManager.setSession(1L, "session-1", 100L)

        val session = activeSessionManager.getSession(1L)

        assertThat(session).isNotNull
        assertThat(session!!.sessionId).isEqualTo("session-1")
        assertThat(session.roomId).isEqualTo(100L)
    }

    @Test
    fun `getSession_등록되지 않은 플레이어는 null을 반환한다`() {
        val session = activeSessionManager.getSession(999L)

        assertThat(session).isNull()
    }

    @Test
    fun `setSession_기존 세션을 새 세션으로 갱신한다`() {
        activeSessionManager.setSession(1L, "session-old", 100L)
        activeSessionManager.setSession(1L, "session-new", 200L)

        val session = activeSessionManager.getSession(1L)

        assertThat(session).isNotNull
        assertThat(session!!.sessionId).isEqualTo("session-new")
        assertThat(session.roomId).isEqualTo(200L)
    }

    @Test
    fun `isActiveSession_현재 활성 세션이면 true를 반환한다`() {
        activeSessionManager.setSession(1L, "session-1", 100L)

        assertThat(activeSessionManager.isActiveSession(1L, "session-1")).isTrue()
    }

    @Test
    fun `isActiveSession_다른 세션이면 false를 반환한다`() {
        activeSessionManager.setSession(1L, "session-1", 100L)

        assertThat(activeSessionManager.isActiveSession(1L, "session-other")).isFalse()
    }

    @Test
    fun `isActiveSession_세션이 없으면 false를 반환한다`() {
        assertThat(activeSessionManager.isActiveSession(999L, "session-1")).isFalse()
    }

    @Test
    fun `removeSession_세션 ID가 일치하면 삭제하고 true를 반환한다`() {
        activeSessionManager.setSession(1L, "session-1", 100L)

        val result = activeSessionManager.removeSession(1L, "session-1")

        assertThat(result).isTrue()
        assertThat(activeSessionManager.getSession(1L)).isNull()
    }

    @Test
    fun `removeSession_세션 ID가 불일치하면 삭제하지 않고 false를 반환한다`() {
        activeSessionManager.setSession(1L, "session-1", 100L)

        val result = activeSessionManager.removeSession(1L, "session-other")

        assertThat(result).isFalse()
        assertThat(activeSessionManager.getSession(1L)).isNotNull()
    }

    @Test
    fun `removeSession_세션이 없으면 false를 반환한다`() {
        val result = activeSessionManager.removeSession(999L, "session-1")

        assertThat(result).isFalse()
    }
}
