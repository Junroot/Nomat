package ilpak.nomat.room.application.domain

/**
 * `RoomStatus.PLAYING` 우산 안에서 라운드 진행을 나타내는 휘발성 단계.
 * `RoomStatus`(멤버십·입장 게이팅)와 분리된 관심사다.
 */
enum class RoundPhase {
    OPEN,
    REVEAL,
    ENDED,
}
