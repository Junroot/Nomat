package ilpak.nomat.room.application.dto

/**
 * 포기 신호 페이로드.
 *
 * `roundSeq`는 클라이언트가 지금 보고 있는 라운드다. 라운드 경계에서 늦게 도착한 신호가 다음 라운드에
 * 꽂히는 것을 막는 CAS 게이트로만 쓰이며, 서버는 불일치하면 조용히 무시한다.
 */
data class RoomPassRequest(
    val roundSeq: Long,
)
