package ilpak.nomat.room.application.domain

/**
 * 게임 종료 이벤트. `playerId`는 종료를 일으킨 방장 — 단, 라운드 엔진의 서버 주도 자연 종료에는
 * 행위자가 없을 수 있어(방장이 이미 떠난 경우) null이 가능하다. ephemeral 브로드캐스트 경로라
 * 직렬화 안정성 부담은 없다.
 */
data class GameEndedEvent(
    val roomId: Long,
    val playerId: Long? = null,
)
