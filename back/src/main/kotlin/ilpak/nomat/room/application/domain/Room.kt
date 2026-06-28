package ilpak.nomat.room.application.domain

import ilpak.nomat.common.exception.ConflictException
import ilpak.nomat.common.exception.ForbiddenException
import ilpak.nomat.common.metadata.AuditMetadata
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import org.hibernate.annotations.BatchSize
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.TableGenerator
import jakarta.persistence.Transient
import org.springframework.data.domain.AbstractAggregateRoot
import org.springframework.data.jpa.domain.support.AuditingEntityListener

@Entity
@EntityListeners(AuditingEntityListener::class)
class Room(
    val title: String,
    val password: String?,
    val maxEntriesCount: Int,
    @Embedded
    val playlist: RoomPlaylist,
    @BatchSize(size = 100)
    @ElementCollection
    @CollectionTable(name = "room_entry", joinColumns = [JoinColumn(name = "room_id")])
    private val entries: MutableList<RoomEntry> = mutableListOf(),
    @Embedded
    val auditMetadata: AuditMetadata = AuditMetadata(),
    @Id
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "room_id_generator")
    @TableGenerator(
        name = "room_id_generator",
        table = "hibernate_sequences",
        pkColumnName = "sequence_name",
        pkColumnValue = "room",
        allocationSize = 1000,
    )
    val id: Long = 0,
) : AbstractAggregateRoot<Room>() {

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "CHAR(20) NOT NULL")
    var status: RoomStatus = RoomStatus.PENDING
        private set

    val master: RoomEntry?
        get() = sortedEntries.firstOrNull()

    val playerIds: Set<Long>
        get() = entries.map { it.playerId }.toSet()

    val playlistMasterId: Long
        get() = playlist.masterId

    val isEmpty: Boolean
        get() = entries.isEmpty()

    @Transient
    private var _sortedEntries: List<RoomEntry>? = null
    val sortedEntries: List<RoomEntry>
        get() {
            if (_sortedEntries == null) {
                _sortedEntries = entries.sortedBy { it.joinDate }
            }
            return requireNotNull(_sortedEntries)
        }

    fun verifyPassword(password: String?) {
        if (this.password != null && this.password != password) {
            throw ForbiddenException("비밀번호가 일치하지 않습니다.")
        }
    }

    fun join(playerId: Long) {
        if (status == RoomStatus.PLAYING) {
            throw ConflictException("게임 중에는 입장할 수 없습니다.")
        }
        if (entries.size >= maxEntriesCount) {
            throw ConflictException("방의 정원이 초과되었습니다.")
        }
        if (playerIds.contains(playerId)) {
            throw ConflictException("이미 방에 입장한 플레이어입니다.")
        }
        entries.add(RoomEntry(playerId))
        _sortedEntries = null
        status = RoomStatus.ACTIVE
        registerEvent(RoomJoinedEvent(id, playerId))
    }

    fun start(playerId: Long) {
        if (master?.playerId != playerId) {
            throw ForbiddenException("방장만 게임을 시작할 수 있습니다.")
        }
        if (status != RoomStatus.ACTIVE) {
            throw ConflictException("시작할 수 없는 방 상태입니다.")
        }
        status = RoomStatus.PLAYING
        registerEvent(GameStartedEvent(id, playerId))
    }

    fun end(playerId: Long) {
        if (master?.playerId != playerId) {
            throw ForbiddenException("방장만 게임을 종료할 수 있습니다.")
        }
        if (status != RoomStatus.PLAYING) {
            throw ConflictException("종료할 수 없는 방 상태입니다.")
        }
        status = RoomStatus.ACTIVE
        registerEvent(GameEndedEvent(id, playerId))
    }

    /**
     * 라운드 엔진의 서버 주도 종료. 방장 권한과 무관하게 `PLAYING→ACTIVE`로 멱등 플립하며
     * 행위자 없는 `GameEndedEvent`를 등록한다. 이미 `PLAYING`이 아니면 아무 일도 하지 않는다(멱등).
     */
    fun endByEngine() {
        if (status != RoomStatus.PLAYING) {
            return
        }
        status = RoomStatus.ACTIVE
        registerEvent(GameEndedEvent(id, null))
    }

    fun leave(playerId: Long) {
        val removed = entries.removeIf { it.playerId == playerId }
        if (!removed) {
            return
        }
        _sortedEntries = null
        registerEvent(RoomLeftEvent(id, playerId))
    }

    companion object {
        const val MAX_TITLE_LENGTH = 30
        const val MAX_PASSWORD_LENGTH = 30
        const val MAX_MAX_ENTRIES_COUNT = 20
    }
}
