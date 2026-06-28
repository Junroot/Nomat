package ilpak.nomat.room.application.dto

/**
 * 게임 종료 브로드캐스트. 방장 수동 종료는 행위자(`playerId`/`nickname`)를 싣고,
 * 라운드 엔진의 서버 주도 자연 종료는 행위자 없이(null) 전한다. 수신 측은 행위자 유무와 무관하게
 * 게임 종료로 처리한다.
 */
data class GameEndedEventMessage(
    override val roomId: Long,
    override val playerId: Long?,
    override val nickname: String?,
) : RoomEventMessage
