import type { ScoreEntry } from "./RoundEvent";

export interface ScoreRow {
    playerId: number;
    nickname: string;
    score: number;
}

// 닉네임이 비어 있을 때만 쓰는 중립 라벨. 퇴장을 단정하지 않는다 —
// 퇴장 여부는 이 자리에서 알 수 없는 정보이고, 그렇게 단정한 것이 이슈 #235의 원인이었다.
const UNKNOWN_NICKNAME = "알 수 없음";

// 내림차순 정렬된 순위. 동점은 원래 순서를 유지한다.
//
// 닉네임은 서버가 이미 실어 보내므로 여기서 조인하지 않는다. 폴백은 백엔드보다 프론트가 먼저
// 배포된 순간만을 위한 것으로, 그때도 화면이 빈칸으로 깨지지 않게 흡수한다.
export function rankScores(scores: ScoreEntry[]): ScoreRow[] {
    return scores
        .map((s) => ({
            playerId: s.playerId,
            nickname: s.nickname || UNKNOWN_NICKNAME,
            score: s.score,
        }))
        .sort((a, b) => b.score - a.score);
}
