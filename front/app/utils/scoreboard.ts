import type { ScoreEntry } from "./RoundEvent";
import type { RoomMemberResponse } from "./RoomDetailResponse";

export interface ScoreRow {
    playerId: number;
    nickname: string;
    score: number;
}

// id만 담긴 점수판을 방의 players와 조인해 닉네임을 붙인다. 미매칭(퇴장)은 "(퇴장)"으로 폴백.
export function joinScores(scores: ScoreEntry[], players: RoomMemberResponse[]): ScoreRow[] {
    const nicknameById = new Map(players.map((p) => [p.id, p.nickname]));
    return scores.map((s) => ({
        playerId: s.playerId,
        nickname: nicknameById.get(s.playerId) ?? "(퇴장)",
        score: s.score,
    }));
}

// 내림차순 정렬된 순위. 동점은 원래 순서를 유지한다.
export function rankScores(scores: ScoreEntry[], players: RoomMemberResponse[]): ScoreRow[] {
    return joinScores(scores, players).sort((a, b) => b.score - a.score);
}
