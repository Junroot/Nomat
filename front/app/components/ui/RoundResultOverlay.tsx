import { rankScores } from "~/utils/scoreboard";
import type { RoundState } from "~/hooks/roundReducer";

interface RoundResultOverlayProps {
    round: RoundState;
    onClose: () => void;
}

const RANK_BADGE = ["🥇", "🥈", "🥉"];

/**
 * 게임 종료(`GAME_ENDED`) 최종 결과(순위) 오버레이.
 * `ENDED`에는 점수판이 없으므로 리듀서가 sticky 보존한 마지막 REVEAL scores를 순위로 그린다.
 * 종료 시점에 방 상태는 이미 `ACTIVE`로 복귀했다 — "방으로"로 닫으면 로비로 돌아간다.
 *
 * 점수판은 자기완결적이다 — 순위·닉네임·점수를 그리는 데 필요한 정보를 점수판 바깥(현재 멤버
 * 목록 등)에서 찾지 않는다. 결과 화면은 게임이 끝난 시점의 기록이므로, 그 이후의 입·퇴장으로
 * 표시 내용이 달라지지 않는다(이슈 #235).
 */
export default function RoundResultOverlay({ round, onClose }: RoundResultOverlayProps) {
    const rows = rankScores(round.scores);
    const topScore = rows.length > 0 ? rows[0].score : 0;

    return (
        <div className="fixed inset-0 z-50 flex flex-col items-center justify-center gap-6 bg-zinc-950/85 backdrop-blur-sm px-6">
            <p className="text-2xl md:text-3xl font-bold text-zinc-100">게임 종료</p>

            <div className="w-full max-w-md flex flex-col gap-2">
                {rows.length === 0 ? (
                    <p className="text-center text-zinc-400">기록된 점수가 없습니다.</p>
                ) : (
                    rows.map((row, index) => {
                        // 최고점을 우승자로 하이라이트(동점 우승 모두 포함).
                        const isWinner = row.score === topScore && topScore > 0;
                        return (
                            <div
                                key={row.playerId}
                                className={`flex items-center justify-between px-4 py-3 rounded-xl ${
                                    isWinner
                                        ? "bg-neon-cyan/15 border border-neon-cyan/40"
                                        : "bg-zinc-800"
                                }`}
                            >
                                <span className="inline-flex items-center gap-3 min-w-0">
                                    <span className="w-6 text-center text-lg">{RANK_BADGE[index] ?? index + 1}</span>
                                    <span className={`truncate ${isWinner ? "font-bold text-zinc-100" : "text-zinc-200"}`}>
                                        {row.nickname}
                                    </span>
                                </span>
                                <span className="font-semibold text-neon-cyan tabular-nums">{row.score}</span>
                            </div>
                        );
                    })
                )}
            </div>

            <button
                type="button"
                className="px-8 py-3 rounded-2xl bg-neon-cyan/20 text-neon-cyan font-semibold hover:bg-neon-cyan/30 transition-colors cursor-pointer"
                onClick={onClose}
            >
                방으로
            </button>
        </div>
    );
}
