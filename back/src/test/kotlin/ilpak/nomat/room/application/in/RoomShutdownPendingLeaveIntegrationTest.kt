package ilpak.nomat.room.application.`in`

import com.fasterxml.jackson.databind.ObjectMapper
import ilpak.nomat.auth.application.TokenService
import ilpak.nomat.infrastructure.integration.IntegrationTest
import ilpak.nomat.infrastructure.integration.step.PlayerStep
import ilpak.nomat.infrastructure.integration.step.PlaylistStep
import ilpak.nomat.infrastructure.integration.step.RoomStep
import ilpak.nomat.infrastructure.integration.step.dummyPlayerRequest
import ilpak.nomat.infrastructure.integration.step.dummyPlaylistCreationRequest
import ilpak.nomat.infrastructure.integration.step.dummyRoomRequest
import ilpak.nomat.infrastructure.integration.util.connectStomp
import ilpak.nomat.room.out.PendingLeaveRedisKeys
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.ApplicationContext
import org.springframework.context.SmartLifecycle
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration

/**
 * 인스턴스 graceful 종료 시 열린 WebSocket 세션의 끊김이 유예 예약으로 기록되는지.
 *
 * 종료 시 Spring은 `SmartLifecycle.stop()` 단계에서 `SubProtocolWebSocketHandler`를 멈추며 열린 세션을 `GOING_AWAY`로 닫고,
 * 그 끊김이 `SessionDisconnectEvent` → `RoomDisconnectListener` → Redis 예약으로 이어진다. 이 단계는 빈 파괴(Redis 연결 포함)보다
 * 앞선다는 것이 Spring 라이프사이클 규약이다. 여기서는 그 라이프사이클 빈만 직접 `stop()`해 "세션 닫힘 → 예약 기록"을 검증한다 —
 * 컨텍스트 전체를 닫으면 Testcontainers(컨텍스트 빈)까지 내려가 같은 JVM의 다른 테스트를 깨뜨리고, `@IntegrationTest`가
 * `classes`를 명시하므로 중첩 `@TestConfiguration` 프로브도 등록되지 않기 때문이다. 검증 후 `start()`로 되돌린다.
 */
@IntegrationTest
class RoomShutdownPendingLeaveIntegrationTest(
    @Autowired private val context: ApplicationContext,
    @Autowired private val playerStep: PlayerStep,
    @Autowired private val playlistStep: PlaylistStep,
    @Autowired private val roomStep: RoomStep,
    @Autowired private val tokenService: TokenService,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val redisTemplate: StringRedisTemplate,
    @LocalServerPort private val port: Int,
) {

    @Test
    fun `WebSocket 라이프사이클이 멈추면 접속 중이던 멤버의 유예 예약이 Redis에 기록된다`() {
        val player = playerStep.save(dummyPlayerRequest())
        val playlist = playlistStep.save(player, dummyPlaylistCreationRequest())
        val room = roomStep.save(player, dummyRoomRequest(playlist.id))
        connectStomp(objectMapper, tokenService, port, player, room.id, "password")

        val pendingMember = PendingLeaveRedisKeys.member(room.id, player.id)
        assertThat(redisTemplate.opsForZSet().score(PendingLeaveRedisKeys.PENDING_LEAVES, pendingMember)).isNull()

        val webSocketHandler = context.getBean("subProtocolWebSocketHandler") as SmartLifecycle
        try {
            webSocketHandler.stop()

            await()
                .pollInterval(Duration.ofMillis(200))
                .atMost(Duration.ofSeconds(5))
                .untilAsserted {
                    assertThat(redisTemplate.opsForZSet().score(PendingLeaveRedisKeys.PENDING_LEAVES, pendingMember))
                        .withFailMessage("라이프사이클 stop이 세션을 닫고 끊김 이벤트가 유예 예약을 기록해야 한다")
                        .isNotNull()
                }
        } finally {
            webSocketHandler.start()
        }
    }
}
