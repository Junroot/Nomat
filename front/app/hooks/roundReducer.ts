// 라운드 상태 머신 — 순수 리듀서.
//
// 서버가 라운드 시계의 주인이다. 이 리듀서는 서버 이벤트/스냅샷에만 전이하며
// `Date.now()`를 절대 읽지 않는다(순수성·테스트 용이성). 표시용 카운트다운은
// 리듀서 밖(useCountdown)이 `deadlineAt`만 읽어 계산한다.
//
// - `roundSeq` 단조 증가 가드로 늦게 도착한 스냅샷/재전송이 더 진행된 상태를 되돌리지 못하게 한다.
// - `scores`·`winnerNickname`은 `GAME_ENDED`에 실려오지 않으므로 마지막 `ROUND_REVEALED` 값을
//   sticky로 보존한다. 승자 닉네임을 `winnerId`와 같은 수명으로 묶어두지 않으면 같은 종류의
//   불일치(이름만 먼저 사라지는 현상)가 다시 생긴다.
import type { RoundPhase, RoundTrackRef, ScoreEntry, RoundSnapshotResponse, RoundStartedEvent, RoundRevealedEvent, RoundPassUpdatedEvent } from "~/utils/RoundEvent";

// "idle" = 게임은 시작됐으나 아직 첫 ROUND_STARTED 전. 그 외는 서버 phase를 그대로 반영.
export type RoundLifecycle = "idle" | RoundPhase;

export interface RoundState {
    phase: RoundLifecycle;
    /**
     * 서버의 전이 CAS epoch. **화면에 쓰지 말 것** — 라운드당 2씩 증가한다
     * (`OPEN→REVEAL`, `REVEAL→다음 OPEN`). 늦게 도착한 스냅샷/재전송이 더 진행된 상태를
     * 되돌리지 못하게 하는 리듀서 내부 단조 가드 전용이다.
     *
     * 표기에 쓰면 `Round 13 / 9`처럼 총 라운드 수를 넘는 값이 나온다. 실제로 두 번 났다 —
     * 라운드 헤더와 정답 공개 표기 양쪽에서. 라운드 번호가 필요하면 {@link RoundState.roundNumber}.
     */
    roundSeq: number;
    /** 사람이 읽는 라운드 번호(1-based). 화면 표기와 라운드 경계 판정은 전부 이 값으로 한다. */
    roundNumber: number;
    totalRounds: number;
    deadlineAt: number | null; // OPEN에서만 유효. REVEAL(라이브)에는 없음
    currentTrack: RoundTrackRef | null;
    // REVEAL에서만 채워지는 다음 라운드 재생 참조 — 플레이어가 미리 버퍼링한다.
    // 마지막 라운드이거나 REVEAL이 아니면 null.
    nextTrack: RoundTrackRef | null;
    title: string | null; // REVEAL/ENDED에서만 채워짐
    winnerId: number | null;
    /** 서버가 해석해 보낸 승자 닉네임. 방 `players`에서 되짚지 않는다(이슈 #235). */
    winnerNickname: string | null;
    scores: ScoreEntry[];
    /** 현재 OPEN 라운드의 포기 인원수. 누가 눌렀는지는 서버가 내려주지 않는다. */
    passedCount: number;
    /** 라운드를 끝내는 데 필요한 포기 인원수. 남은 인원이 줄면 함께 내려간다. */
    requiredCount: number;
    /** 본인이 포기 중인지. 서버가 판정해 스냅샷/토글 결과로만 알려준다. */
    passed: boolean;
}

export const initialRoundState: RoundState = {
    phase: "idle",
    roundSeq: 0,
    roundNumber: 0,
    totalRounds: 0,
    deadlineAt: null,
    currentTrack: null,
    nextTrack: null,
    title: null,
    winnerId: null,
    winnerNickname: null,
    scores: [],
    passedCount: 0,
    requiredCount: 0,
    passed: false,
};

export type RoundAction =
    | { type: "GAME_STARTED" }
    | { type: "ROUND_STARTED"; event: RoundStartedEvent }
    | { type: "ROUND_REVEALED"; event: RoundRevealedEvent }
    | { type: "PASS_UPDATED"; event: RoundPassUpdatedEvent }
    | { type: "PASS_TOGGLED" }
    | { type: "GAME_ENDED" }
    | { type: "HYDRATE"; snapshot: RoundSnapshotResponse }
    | { type: "RESET" };

export function roundReducer(state: RoundState, action: RoundAction): RoundState {
    switch (action.type) {
        case "GAME_STARTED":
            // 새 게임 진입 — 이전 라운드 잔재를 비우고 첫 ROUND_STARTED를 기다린다.
            return initialRoundState;

        case "ROUND_STARTED": {
            const e = action.event;
            // 단조 가드: 이미 같거나 더 진행된 라운드면 무시(재전송·경합 방어).
            if (e.roundSeq < state.roundSeq) return state;
            if (e.roundSeq === state.roundSeq && state.phase !== "idle") return state;
            return {
                phase: "OPEN",
                roundSeq: e.roundSeq,
                roundNumber: e.roundNumber,
                totalRounds: e.totalRounds,
                deadlineAt: e.deadlineAt,
                currentTrack: {
                    embedId: e.embedId,
                    startTimeSec: e.startTimeSec,
                    endTimeSec: e.endTimeSec,
                    repeatCount: e.repeatCount,
                },
                // 선버퍼링해둔 트랙이 이제 현재 트랙이 됐다 — 예약을 비워 재선버퍼링을 막는다.
                nextTrack: null,
                title: null,
                winnerId: null,
                winnerNickname: null,
                scores: state.scores, // 점수판은 라운드를 가로질러 유지
                // 포기 현황은 라운드 단위 상태다 — 이월되면 새 곡을 듣기도 전에 카운트가 차 있다.
                passedCount: 0,
                requiredCount: 0,
                passed: false,
            };
        }

        case "ROUND_REVEALED": {
            const e = action.event;
            // REVEAL은 현재 OPEN 라운드와 같은 seq로 온다 → 더 낮은 seq만 무시.
            if (e.roundSeq < state.roundSeq) return state;
            return {
                ...state,
                phase: "REVEAL",
                roundSeq: e.roundSeq,
                deadlineAt: null, // 라이브 REVEAL엔 마감이 없다 — 다음 이벤트까지 대기
                nextTrack: e.nextTrack,
                title: e.title,
                winnerId: e.winnerId,
                winnerNickname: e.winnerNickname,
                scores: e.scores,
            };
        }

        case "PASS_UPDATED": {
            const e = action.event;
            // ⚠️ 가드는 `<`여야 한다. 포기 이벤트는 **현재 OPEN과 같은 roundSeq로 온다** —
            // ROUND_STARTED/HYDRATE의 `=== && phase !== "idle"` 패턴을 복사하면 전부 씹힌다.
            if (e.roundSeq < state.roundSeq) return state;
            // 카운트를 max()로 누적하지 않고 마지막 값을 그대로 반영한다. 포기는 토글이라
            // 인원수가 줄어들 수 있고, max()를 쓰면 취소가 영영 반영되지 않는다.
            return { ...state, passedCount: e.passedCount, requiredCount: e.requiredCount };
        }

        case "PASS_TOGGLED":
            // 본인 여부는 브로드캐스트에 실리지 않으므로(익명) 자기 조작으로만 뒤집는다.
            // 서버가 무시한 신호(라운드 경계·마감 이후)면 다음 스냅샷이 정정한다.
            return { ...state, passed: !state.passed };

        case "GAME_ENDED":
            // ENDED에는 점수판이 없다 → 마지막 REVEAL의 scores·winnerNickname을 sticky 보존.
            return { ...state, phase: "ENDED", deadlineAt: null };

        case "HYDRATE": {
            const s = action.snapshot;
            // 늦은 하이드레이션이 더 진행된 라이브 상태를 되돌리지 않도록 가드.
            if (s.roundSeq < state.roundSeq) return state;
            if (s.roundSeq === state.roundSeq && state.phase !== "idle") return state;
            return {
                phase: s.phase,
                roundSeq: s.roundSeq,
                roundNumber: s.roundNumber,
                totalRounds: s.totalRounds,
                deadlineAt: s.phase === "OPEN" ? s.deadlineAt : null,
                currentTrack: s.currentTrack,
                // REVEAL 중 재접속하면 이벤트를 놓쳤어도 여기서 선버퍼링 기회를 받는다.
                nextTrack: s.nextTrack,
                title: s.title,
                winnerId: s.winnerId,
                winnerNickname: s.winnerNickname,
                scores: s.scores,
                // 스냅샷 값을 그대로 반영한다 — 새로고침으로 잃은 본인의 토글 상태를 여기서 복원한다.
                passedCount: s.passedCount,
                requiredCount: s.requiredCount,
                passed: s.passed,
            };
        }

        case "RESET":
            return initialRoundState;

        default:
            return state;
    }
}
