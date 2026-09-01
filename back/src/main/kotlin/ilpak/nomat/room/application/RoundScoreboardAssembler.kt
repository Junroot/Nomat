package ilpak.nomat.room.application

import ilpak.nomat.player.application.PlayerService
import ilpak.nomat.room.application.domain.ScoreEntry
import ilpak.nomat.room.application.dto.ScoreEntryResponse
import org.springframework.stereotype.Component

/**
 * 점수판 id를 닉네임으로 해석해 전송 DTO를 만드는 단일 지점.
 *
 * 해석 기준은 방 멤버십이 아니라 `player` 저장소다. 멤버 목록은 퇴장 즉시 줄어드는 반면 점수판
 * 스냅샷은 다음 공개까지(종료 후에는 영구히) 유지되므로, 멤버십으로 이름을 되짚으면 이미 떠난
 * 참가자가 이름을 잃는다(이슈 #235).
 *
 * 점수판 id와 승자 id를 합친 집합으로 배치 조회 1회만 수행한다 — 승자 닉네임을 위한 추가 조회는 없다.
 * 승자를 점수판에서 역참조하지 않는 이유는 **점수 항목이 없는 승자**가 성립하기 때문이다:
 * `ADVANCE_ON_CORRECT_SCRIPT`는 아직 멤버일 때만 가점하지만 `winnerId`는 무조건 기록한다.
 *
 * 해석되지 않는 id는 예외가 아니라 [UNKNOWN_NICKNAME]으로 degrade 한다. 여기서 예외가 나면 이름
 * 하나 때문에 그 라운드의 공개 방송 자체가 죽는다. `(퇴장)` 같은 라벨은 쓰지 않는다 — 퇴장 여부는
 * 이 지점에서 알 수 없는 정보이고, 그렇게 단정한 것이 애초의 결함이었다.
 */
@Component
class RoundScoreboardAssembler(
    private val playerService: PlayerService,
) {

    fun assemble(scores: List<ScoreEntry>, winnerId: Long?): Scoreboard {
        val ids = scores.mapTo(mutableSetOf()) { it.playerId }
        winnerId?.let { ids.add(it) }
        val nicknameById = if (ids.isEmpty()) {
            emptyMap()
        } else {
            playerService.findByIdIn(ids).associate { it.id to it.nickname }
        }
        return Scoreboard(
            entries = scores.map {
                ScoreEntryResponse(
                    playerId = it.playerId,
                    nickname = nicknameById[it.playerId] ?: UNKNOWN_NICKNAME,
                    score = it.score,
                )
            },
            winnerNickname = winnerId?.let { nicknameById[it] ?: UNKNOWN_NICKNAME },
        )
    }

    companion object {
        const val UNKNOWN_NICKNAME = "알 수 없음"
    }
}

/** 닉네임까지 해석된 점수판. `winnerNickname`은 승자가 없는 타임아웃 공개에서 null. */
data class Scoreboard(
    val entries: List<ScoreEntryResponse>,
    val winnerNickname: String?,
)
