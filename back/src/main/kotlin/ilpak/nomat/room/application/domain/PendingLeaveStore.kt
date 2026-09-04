package ilpak.nomat.room.application.domain

/**
 * 퇴장 유예 예약 포트 — "끊김 → 유예 → 퇴장" 전이의 예약을 **프로세스 밖(인스턴스 공유 저장소)** 에 둔다.
 *
 * 예약이 JVM 메모리에만 있으면 롤링 배포·재시작 한 번에 사라져 방이 영영 남고, 복수 replica에서는
 * 끊김을 처리한 인스턴스와 재접속을 받은 인스턴스가 달라 취소가 성립하지 않는다. 그래서 예약·취소·만료 조회를
 * 전부 저장소 연산으로 두고, 어느 인스턴스가 호출해도 같은 결과가 나오게 한다.
 *
 * - [remove]는 **취소와 claim을 겸한다.** 반환값이 "예약이 있었는가"이며, 재접속(취소)과 sweeper(claim)가
 *   같은 항목을 두고 경합해도 정확히 한쪽만 `true`를 받는다(claim-then-act). 그래서 접속 중인 멤버가
 *   만료 처리에 휩쓸려 퇴장되는 일이 없다.
 * - 시각 앵커(만료 시각·만료 판정)는 앱 시계가 아니라 저장소 시계(Redis `TIME`)다. replica 간 시계 스큐를
 *   정합성 문제가 아닌 지연 문제로 강등하는 라운드 엔진의 원칙을 그대로 따른다.
 */
interface PendingLeaveStore {

    /** 유예 예약. 만료 시각 = 저장소 현재 시각 + [graceSeconds]. 같은 (room, player)의 기존 예약은 덮어쓴다. */
    fun schedule(roomId: Long, playerId: Long, graceSeconds: Long)

    /** 예약을 제거한다. 예약이 있었으면 `true`. 취소(재접속)·claim(sweeper) 모두 이 연산이다. */
    fun remove(roomId: Long, playerId: Long): Boolean

    /** 만료 시각이 지난 예약 목록. 저장소 시계 기준이며 항목은 제거하지 않는다(제거는 [remove]로 claim). */
    fun findDue(): List<PendingLeave>

    /**
     * claim 후 퇴장에 실패한 항목을 되돌린다. 원래 만료 시각이 아니라 **저장소 현재 시각 + 재시도 간격**으로
     * 다시 예약한다 — 매 틱 재시도로 로그가 폭주하지 않게 하고, 항목이 과거 시각에 머물러 노화하지 않게 한다.
     */
    fun restore(roomId: Long, playerId: Long)
}

/**
 * 만료된 유예 예약 한 건. 만료 시각(score)은 싣지 않는다 — [PendingLeaveStore.restore]가 원래 시각을
 * 필요로 하지 않으므로(현재 시각 + 재시도 간격) 조회가 member만 읽으면 된다.
 */
data class PendingLeave(val roomId: Long, val playerId: Long)
