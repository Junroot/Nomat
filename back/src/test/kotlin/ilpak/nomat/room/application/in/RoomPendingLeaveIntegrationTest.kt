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
import ilpak.nomat.infrastructure.integration.util.auth
import ilpak.nomat.infrastructure.integration.util.connectStomp
import ilpak.nomat.player.application.dto.PlayerResponse
import ilpak.nomat.playlist.application.dto.PlaylistWithTrackResponse
import ilpak.nomat.room.application.RoomService
import ilpak.nomat.room.application.domain.PendingLeave
import ilpak.nomat.room.application.domain.PendingLeaveStore
import ilpak.nomat.room.application.dto.RoomDetailResponse
import ilpak.nomat.room.out.PendingLeaveRedisKeys
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import java.time.Duration

/**
 * 유예 예약이 프로세스 밖(Redis)에 있어 인스턴스 무관하게 취소·만료 처리되는지 — 서비스 직접 호출로 "다른 인스턴스"를 흉내 낸다.
 * 두 인스턴스는 같은 Redis를 보므로 STOMP 인스턴스를 둘 띄우지 않아도 같은 경로가 재현된다.
 */
@IntegrationTest
class RoomPendingLeaveIntegrationTest(
    @Autowired private val client: WebTestClient,
    @Autowired private val playerStep: PlayerStep,
    @Autowired private val playlistStep: PlaylistStep,
    @Autowired private val roomStep: RoomStep,
    @Autowired private val tokenService: TokenService,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val roomService: RoomService,
    @Autowired private val pendingLeaveStore: PendingLeaveStore,
    @Autowired private val redisTemplate: StringRedisTemplate,
    @Autowired @Qualifier("taskScheduler") private val taskScheduler: ThreadPoolTaskScheduler,
    @LocalServerPort private val port: Int,
) {

    private lateinit var player: PlayerResponse
    private lateinit var playlist: PlaylistWithTrackResponse

    @BeforeEach
    fun setUp() {
        player = playerStep.save(dummyPlayerRequest())
        playlist = playlistStep.save(player, dummyPlaylistCreationRequest())
    }

    @Test
    fun `다른 인스턴스가 기록한 유예 예약을 취소하면 유예가 지나도 퇴장되지 않는다`() {
        val room = roomStep.save(player, dummyRoomRequest(playlist.id))
        val session = connectStomp(objectMapper, tokenService, port, player, room.id, "password")

        // 인스턴스 A가 끊김을 처리해 예약을 기록했다고 치고, 인스턴스 B(여기)가 취소한다.
        roomService.scheduleLeave(room.id, player.id)
        assertThat(pendingLeaveStore.findDue()).doesNotContain(PendingLeave(room.id, player.id))
        assertThat(roomService.cancelPendingLeave(room.id, player.id)).isTrue()
        assertThat(roomService.cancelPendingLeave(room.id, player.id)).isFalse()

        // 유예(2초) + sweeper 주기(1초) + 여유가 지나도 멤버십이 남아 있어야 한다.
        Thread.sleep(4000)
        assertThat(getRoomDetail(room.id)?.players?.map { it.id }).contains(player.id)

        session.disconnect()
    }

    @Test
    fun `재접속이 먼저 claim한 항목은 sweeper가 건너뛴다`() {
        val room = roomStep.save(player, dummyRoomRequest(playlist.id))
        val session = connectStomp(objectMapper, tokenService, port, player, room.id, "password")

        // 이미 만료된 예약을 두고, 재접속(취소)이 먼저 ZREM으로 가져간 뒤 sweep이 돈다.
        pendingLeaveStore.schedule(room.id, player.id, 0)
        assertThat(pendingLeaveStore.findDue()).contains(PendingLeave(room.id, player.id))
        assertThat(roomService.cancelPendingLeave(room.id, player.id)).isTrue()

        roomService.sweepDueLeaves()

        assertThat(getRoomDetail(room.id)?.players?.map { it.id }).contains(player.id)
        session.disconnect()
    }

    @Test
    fun `퇴장이 락 획득 실패로 예외를 던지면 복원되어 락 해제 후 재시도된다`() {
        val room = roomStep.save(player, dummyRoomRequest(playlist.id))
        val lockKey = "room:${room.id}:lock"
        val session = connectStomp(objectMapper, tokenService, port, player, room.id, "password")

        // 멤버십 락을 테스트가 선점한 채로 유일 멤버의 유예를 만료시킨다 → leave가 ConflictException → 복원.
        redisTemplate.opsForValue().set(lockKey, "held-by-test", Duration.ofSeconds(60))
        session.disconnect()

        // 유예(2초) + 락 대기(최대 5초) + 복원 간격(5초)을 넘겨도 방은 그대로다.
        Thread.sleep(9000)
        assertThat(getRoomDetail(room.id)?.players?.map { it.id }).contains(player.id)
        assertThat(
            redisTemplate.opsForZSet().score(PendingLeaveRedisKeys.PENDING_LEAVES, PendingLeaveRedisKeys.member(room.id, player.id)),
        ).withFailMessage("실패한 항목은 복원돼 ZSET에 남아 있어야 한다").isNotNull()

        redisTemplate.delete(lockKey)

        await()
            .pollInterval(Duration.ofMillis(500))
            .atMost(Duration.ofSeconds(15))
            .untilAsserted {
                client.get().uri("/rooms/{roomId}", room.id)
                    .auth(player)
                    .exchange()
                    .expectStatus().isNotFound()
            }
        assertThat(pendingLeaveStore.findDue()).doesNotContain(PendingLeave(room.id, player.id))
    }

    @Test
    fun `존재하지 않는 방의 예약은 예외 없이 완료되어 재시도되지 않는다`() {
        val ghostRoomId = 8_000_000L
        pendingLeaveStore.schedule(ghostRoomId, player.id, 0)
        assertThat(pendingLeaveStore.findDue()).contains(PendingLeave(ghostRoomId, player.id))

        roomService.sweepDueLeaves()

        assertThat(pendingLeaveStore.findDue()).doesNotContain(PendingLeave(ghostRoomId, player.id))
        assertThat(
            redisTemplate.opsForZSet().score(PendingLeaveRedisKeys.PENDING_LEAVES, PendingLeaveRedisKeys.member(ghostRoomId, player.id)),
        ).isNull()
    }

    @Test
    fun `@Scheduled 작업은 이름이 taskScheduler인 전용 풀에서 돈다`() {
        // 이름 기반 해석이 깨져 단일 스레드 로컬 executor로 폴백하면 이 풀의 큐에는 아무 작업도 없다.
        assertThat(taskScheduler.poolSize).isEqualTo(4)
        assertThat(taskScheduler.threadNamePrefix).isEqualTo("scheduling-")
        // 유예 sweeper·라운드 마감 sweeper·이벤트 재발행 스케줄러
        assertThat(taskScheduler.scheduledThreadPoolExecutor.queue.size).isGreaterThanOrEqualTo(3)
    }

    private fun getRoomDetail(roomId: Long): RoomDetailResponse? {
        return client.get().uri("/rooms/{roomId}", roomId)
            .auth(player)
            .exchange()
            .expectStatus().isOk()
            .expectBody<RoomDetailResponse>()
            .returnResult()
            .responseBody
    }
}
