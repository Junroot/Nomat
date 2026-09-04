package ilpak.nomat.room.application.`in`

import ilpak.nomat.auth.application.TokenService
import ilpak.nomat.infrastructure.integration.IntegrationTest
import ilpak.nomat.infrastructure.integration.step.PlayerStep
import ilpak.nomat.infrastructure.integration.step.PlaylistStep
import ilpak.nomat.infrastructure.integration.step.RoomStep
import ilpak.nomat.infrastructure.integration.step.dummyPlayerRequest
import ilpak.nomat.infrastructure.integration.step.dummyPlaylistCreationRequest
import ilpak.nomat.infrastructure.integration.step.dummyRoomRequest
import ilpak.nomat.room.out.PendingLeaveRedisKeys
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.net.URI
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 서버 하트비트 미수신 감지 — 클라이언트가 `heart-beat:10000,10000`으로 협상해 놓고 침묵하면 서버가 협상 간격의 3배
 * (≈30초) 안에 세션을 닫고, 그 끊김이 일반 끊김과 같은 경로로 유예 예약이 되는지.
 *
 * `WebSocketStompClient`는 `heart-beat` 헤더가 있으면 `TaskScheduler`를 강제해(클라이언트가 하트비트를 보내 버린다)
 * 쓸 수 없으므로 raw 세션으로 CONNECT 프레임 텍스트를 직접 보낸다.
 *
 * `@Tag("slow")`는 표식일 뿐이다 — 빌드에 태그 필터가 없어 CI에서도 실행된다.
 */
@Tag("slow")
@IntegrationTest
class RoomHeartbeatTimeoutIntegrationTest(
    @Autowired private val playerStep: PlayerStep,
    @Autowired private val playlistStep: PlaylistStep,
    @Autowired private val roomStep: RoomStep,
    @Autowired private val tokenService: TokenService,
    @Autowired private val redisTemplate: StringRedisTemplate,
    @LocalServerPort private val port: Int,
) {

    @Test
    fun `하트비트를 협상하고 침묵하면 서버가 세션을 닫고 유예가 예약된다`() {
        val player = playerStep.save(dummyPlayerRequest())
        val playlist = playlistStep.save(player, dummyPlaylistCreationRequest())
        val room = roomStep.save(player, dummyRoomRequest(playlist.id))

        val closed = CountDownLatch(1)
        val connected = CountDownLatch(1)
        val handler = object : TextWebSocketHandler() {
            override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
                if (message.payload.startsWith("CONNECTED")) {
                    connected.countDown()
                }
            }

            override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
                closed.countDown()
            }
        }
        val headers = WebSocketHttpHeaders()
        headers.add("Cookie", "${TokenService.TOKEN_COOKIE_KEY}=${tokenService.getNewToken(player.id)}")
        val session = StandardWebSocketClient()
            .execute(handler, headers, URI("ws://localhost:$port/ws"))
            .get(5, TimeUnit.SECONDS)

        // STOMP 프레임은 NUL(`\u0000`)로 끝난다.
        val connectFrame = "CONNECT\naccept-version:1.2\nheart-beat:10000,10000\nroomId:${room.id}\npassword:password\n\n\u0000"
        session.sendMessage(TextMessage(connectFrame))
        assertThat(connected.await(5, TimeUnit.SECONDS)).withFailMessage("CONNECTED 프레임을 받지 못했다").isTrue()

        // 이후 어떤 프레임도 보내지 않는다. 서버는 읽기 간격(10초)의 3배 동안 침묵하면 세션을 닫는다.
        val pendingMember = PendingLeaveRedisKeys.member(room.id, player.id)
        assertThat(redisTemplate.opsForZSet().score(PendingLeaveRedisKeys.PENDING_LEAVES, pendingMember)).isNull()

        assertThat(closed.await(50, TimeUnit.SECONDS)).withFailMessage("서버가 침묵한 세션을 닫지 않았다").isTrue()
        await()
            .pollInterval(Duration.ofMillis(500))
            .atMost(Duration.ofSeconds(5))
            .untilAsserted {
                assertThat(redisTemplate.opsForZSet().score(PendingLeaveRedisKeys.PENDING_LEAVES, pendingMember)).isNotNull()
            }
    }
}
