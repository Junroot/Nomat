package ilpak.nomat.infrastructure.web

import ilpak.nomat.room.application.RoomService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Component
class ReconnectGracePeriodManager(
    private val roomService: RoomService,
    @Value("\${app.room.reconnect-grace-period-seconds:60}") private val gracePeriodSeconds: Long,
) {

    private val scheduler = Executors.newScheduledThreadPool(5)
    private val pendingLeaves = ConcurrentHashMap<PendingLeaveKey, ScheduledFuture<*>>()

    fun scheduleLeave(roomId: Long, playerId: Long) {
        val key = PendingLeaveKey(roomId, playerId)
        val future = scheduler.schedule(
            {
                pendingLeaves.remove(key)
                roomService.leave(roomId, playerId)
                log.info("유예 시간 만료로 퇴장 처리: roomId={}, playerId={}", roomId, playerId)
            },
            gracePeriodSeconds,
            TimeUnit.SECONDS,
        )
        pendingLeaves[key] = future
    }

    fun cancelGracePeriod(roomId: Long, playerId: Long): Boolean {
        val key = PendingLeaveKey(roomId, playerId)
        val future = pendingLeaves.remove(key) ?: return false
        future.cancel(false)
        log.info("재접속으로 유예 취소: roomId={}, playerId={}", roomId, playerId)
        return true
    }

    private data class PendingLeaveKey(val roomId: Long, val playerId: Long)

    companion object {
        private val log = LoggerFactory.getLogger(ReconnectGracePeriodManager::class.java)
    }
}
