// 서버 주도 라운드 엔진의 실시간 프로토콜 타입.
// 백엔드 `add-game-round-engine`이 확정한 STOMP 이벤트·재접속 스냅샷 계약을 그대로 소비한다.
// 소스 오브 트루스: back/.../room/application/dto/{RoundStartedEventMessage,RoundRevealedEventMessage,RoundSnapshotResponse}.kt

export type RoundPhase = "OPEN" | "REVEAL" | "ENDED";

// ⚠️ `roundSeq`와 `roundNumber`는 다르다.
// - `roundSeq`   전이마다 +1 되는 서버의 CAS epoch. **라운드당 2씩 증가한다**
//                (OPEN→REVEAL, REVEAL→다음 OPEN). 늦게 도착한 스냅샷이 더 진행된 상태를
//                되돌리지 못하게 하는 단조 가드 용도로만 쓴다.
// - `roundNumber` 사람이 읽는 라운드 번호(1-based). 화면 표기와 "라운드가 바뀌었는가" 판정은
//                이 값으로 한다. `roundSeq`를 표기에 쓰면 `13 / 9` 같은 값이 나온다.

// 점수판 항목 — 닉네임은 서버가 `player` 저장소에서 해석해 실어 보낸다.
// 방 `players`와 조인하지 말 것: 멤버 목록은 퇴장 즉시 줄어드는 반면 점수판은 다음 공개까지
// (종료 후에는 영구히) 유지되므로, 조인하면 이미 떠난 참가자의 이름이 사라진다(이슈 #235).
export interface ScoreEntry {
    playerId: number;
    nickname: string;
    score: number;
}

// answer-stripped 재생 참조. 정답(title)은 담기지 않는다.
export interface RoundTrackRef {
    embedId: string;
    startTimeSec: number;
    endTimeSec: number;
    repeatCount: number;
}

// 재접속 복원용 라운드 스냅샷 (`GET /rooms/{roomId}` 응답의 `round`).
// phase로 게이팅되어 OPEN 중에는 title이 null이다.
export interface RoundSnapshotResponse {
    phase: RoundPhase;
    roundSeq: number;
    roundNumber: number;
    totalRounds: number;
    deadlineAt: number; // epoch ms
    currentTrack: RoundTrackRef;
    title: string | null;
    winnerId: number | null;
    // 승자 닉네임. 점수판에서 역참조하지 않고 서버가 따로 싣는다 — 가점은 아직 멤버일 때만
    // 적용되는 반면 winnerId는 무조건 기록되므로 점수 항목이 없는 승자가 성립한다.
    winnerNickname: string | null;
    scores: ScoreEntry[];
    // REVEAL 단계에서만 채워진다. REVEAL 중 재접속한 멤버가 ROUND_REVEALED를 놓쳐
    // 혼자만 선버퍼링을 못 하면 불균등이 그대로 재현되므로 스냅샷에도 싣는다.
    nextTrack: RoundTrackRef | null;
}

// `ROUND_STARTED` — answer-stripped 재생 참조. 행위자가 없어 playerId/nickname은 null.
export interface RoundStartedEvent {
    type: "ROUND_STARTED";
    roomId: number;
    roundSeq: number;
    roundNumber: number;
    totalRounds: number;
    deadlineAt: number; // epoch ms
    embedId: string;
    startTimeSec: number;
    endTimeSec: number;
    repeatCount: number;
    playerId: null;
    nickname: null;
}

// `ROUND_REVEALED` — 정답·승자·갱신된 점수판. winnerId=null이면 타임아웃. 행위자 없음.
export interface RoundRevealedEvent {
    type: "ROUND_REVEALED";
    roomId: number;
    roundSeq: number;
    winnerId: number | null;
    // 승자 닉네임. 타임아웃(winnerId=null)이면 null.
    winnerNickname: string | null;
    title: string;
    scores: ScoreEntry[];
    // 다음 라운드의 answer-stripped 재생 참조. REVEAL 구간 동안 선버퍼링해 라운드 시작 시
    // 로드·버퍼링 지연을 없앤다. 마지막 라운드에서는 null.
    nextTrack: RoundTrackRef | null;
    playerId: null;
    nickname: null;
}
