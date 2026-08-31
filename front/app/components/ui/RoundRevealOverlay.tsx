import type { RoundState } from "~/hooks/roundReducer";
import type { RoomMemberResponse } from "~/utils/RoomDetailResponse";

interface RoundRevealOverlayProps {
    round: RoundState;
    players: RoomMemberResponse[];
}

/**
 * `ROUND_REVEALED` 정답 공개 오버레이. 다음 `ROUND_STARTED`가 phase를 OPEN으로 바꾸면 자동으로 사라진다.
 * 정규 정답(`title`)을 표기로 쓰고, 승자가 있으면 하이라이트, 없으면(타임아웃) "시간 초과"로 표시한다.
 *
 * ⚠️ 이 구간에는 `ClipPlayer`가 정답 영상을 fixed로 띄운다. 영상은 iframe이라 DOM으로 옮기면
 * 재로드되므로(채워둔 버퍼가 날아간다) 이 오버레이의 자식이 될 수 없다. 두 요소가 서로를 모른 채
 * 각자 뷰포트 기준으로 배치되므로, **상단 여백을 영상과 똑같은 식으로 계산해 맞물린다**:
 *
 * ```
 *  영상   top: 8vh              높이: min(92vw,720px,80vh) * 9/16
 *  텍스트 pt : 8vh + 그 높이 + 1.5rem   ← 영상 바로 아래에서 시작
 * ```
 *
 * 둘 중 하나만 바꾸면 겹치거나 사이가 벌어진다. `ClipPlayer`의 `visible` 클래스와 함께 볼 것.
 */
export default function RoundRevealOverlay({ round, players }: RoundRevealOverlayProps) {
    const winnerNickname =
        round.winnerId == null ? null : players.find((p) => p.id === round.winnerId)?.nickname ?? "(퇴장)";

    return (
        <div className="fixed inset-0 z-40 flex flex-col items-center justify-start gap-4 pt-[calc(8vh_+_min(92vw,720px,80vh)_*_0.5625_+_1.5rem)] bg-zinc-950/85 backdrop-blur-sm px-6 text-center">
            <p className="text-sm font-semibold tracking-widest text-zinc-400">
                Round {round.roundNumber} / {round.totalRounds}
            </p>
            {winnerNickname ? (
                <>
                    <p className="text-neon-green text-lg font-semibold">🎉 {winnerNickname} 정답!</p>
                    <p className="text-3xl md:text-4xl font-bold text-zinc-100 break-words">{round.title}</p>
                </>
            ) : (
                <>
                    <p className="text-neon-pink text-lg font-semibold">⏱ 시간 초과</p>
                    <p className="text-xl md:text-2xl text-zinc-300">
                        정답은 <span className="font-bold text-zinc-100">{round.title}</span>
                    </p>
                </>
            )}
            <p className="text-sm text-zinc-500 mt-2">다음 라운드를 기다리는 중…</p>
        </div>
    );
}
