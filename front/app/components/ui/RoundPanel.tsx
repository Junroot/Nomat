import MusicIcon from "~/assets/play.svg?react";
import useCountdown from "~/hooks/useCountdown";
import { rankScores } from "~/utils/scoreboard";
import type { ClipPlaybackStatus } from "~/hooks/useClipPlayback";
import type { RoundState } from "~/hooks/roundReducer";

interface RoundPanelProps {
    round: RoundState;
    // 재생 상태. 오디오 플레이어는 방 세션 자원이라 이 패널이 아니라 방 화면이 소유한다
    // (게임 시작 전에 만들어 부트스트랩을 미리 끝내야 하므로) — 여기서는 안내 UI를 위해 값만 받는다.
    playback: ClipPlaybackStatus;
}

function formatRemaining(ms: number): string {
    const totalSec = Math.ceil(ms / 1000);
    const m = Math.floor(totalSec / 60);
    const s = totalSec % 60;
    return `${m}:${s.toString().padStart(2, "0")}`;
}

/**
 * PLAYING 중 인-플로우 라운드 UI — 라운드 헤더, 표시용 카운트다운, **정답 공개**, 점수판,
 * 재생 실패 안내. 최종 결과(ENDED)만 별도 오버레이가 담당한다.
 *
 * 정답 공개는 전체화면 오버레이가 아니라 이 패널 안에 인라인으로 들어간다. 화면을 덮으면
 * 그 5초 동안 채팅 피드·입력창이 함께 가려지는데, 서버는 그 구간에도 채팅을 방송하므로
 * 순전히 클라이언트가 만드는 손실이었다. 여기에 두면 "정답 → 승자 → 갱신된 점수"가
 * 한 덩어리로 읽히기도 한다 — 점수는 `ROUND_REVEALED`에 실려 오므로 그 순간에 갱신된다.
 *
 * 오디오 재생은 여기서 다루지 않는다. 플레이어는 **게임 시작 전부터** 살아 있어야 하는
 * 방 세션 자원이라(부트스트랩을 미리 끝내기 위해) 이 패널보다 수명이 길다.
 */
export default function RoundPanel({ round, playback }: RoundPanelProps) {
    const remaining = useCountdown(round.deadlineAt);
    const rows = rankScores(round.scores);
    const isOpen = round.phase === "OPEN";
    const isReveal = round.phase === "REVEAL";

    return (
        // 오버레이의 dim이 사라진 만큼의 시각적 강조는 패널 자체가 진다 — 기존 `shadow-glow-cyan`
        // 유틸리티(app.css)를 그대로 쓰고 새 키프레임·유틸리티를 만들지 않는다.
        <div
            className={
                "flex-1 min-w-0 p-3 md:p-4 flex flex-col gap-3 bg-zinc-800 rounded-2xl transition-shadow duration-200" +
                (isReveal ? " ring-1 ring-neon-cyan/40 shadow-glow-cyan" : "")
            }
        >
            <div className="flex items-center justify-between">
                <div className="inline-flex items-center gap-2 font-bold text-lg md:text-xl">
                    <MusicIcon className="size-5 md:size-6 text-neon-cyan" />
                    {round.phase === "idle" ? (
                        <span className="text-zinc-200">게임 시작 중…</span>
                    ) : (
                        <span className="text-zinc-100">
                            {/* roundSeq가 아니라 roundNumber다 — roundSeq는 전이마다 +1 되는
                                CAS epoch라 표기에 쓰면 "13 / 9"처럼 총 라운드 수를 넘는다. */}
                            Round {round.roundNumber}
                            <span className="text-zinc-500"> / {round.totalRounds}</span>
                        </span>
                    )}
                </div>
                {isOpen && (
                    <span className="tabular-nums font-mono text-lg md:text-2xl font-bold text-neon-cyan">
                        {remaining != null && remaining > 0 ? `⏱ ${formatRemaining(remaining)}` : "판정 중…"}
                    </span>
                )}
            </div>

            {/* 정답 공개 — 라운드 번호는 헤더의 `roundNumber`가 이미 쓰고 있으므로 여기서는
                정답과 승자만 보인다. 승자 이름은 서버가 해석해 보낸 값을 그대로 쓴다.
                방 `players`에서 되짚으면 정답자가 공개 직후 나가는 순간 이름이 사라진다(이슈 #235). */}
            {isReveal && (
                <div className="flex flex-col items-center gap-1 text-center">
                    {round.winnerNickname ? (
                        <p className="text-neon-green text-sm md:text-base font-semibold">🎉 {round.winnerNickname} 정답!</p>
                    ) : (
                        <p className="text-neon-pink text-sm md:text-base font-semibold">⏱ 시간 초과</p>
                    )}
                    <p className="text-xl md:text-3xl font-bold text-zinc-100 break-words">{round.title}</p>
                </div>
            )}

            {rows.length > 0 && (
                <div className="flex flex-col gap-1">
                    {rows.map((row) => (
                        <div key={row.playerId} className="flex items-center justify-between px-2 py-1 rounded-lg bg-zinc-900/40">
                            <span className="text-sm text-zinc-200 truncate">{row.nickname}</span>
                            <span className="text-sm font-semibold text-neon-cyan tabular-nums">{row.score}</span>
                        </div>
                    ))}
                </div>
            )}

            {/* 재생 실패 안내 — 라운드는 서버 권위로 계속되므로 여기서 종료·스킵하지 않는다 */}
            {isOpen && playback === "unplayable" && (
                <div className="px-3 py-2 rounded-xl bg-warning/10 border border-warning/30 text-sm text-warning">
                    이 곡은 재생할 수 없어요. 내 오디오 설정 문제가 아니라 곡 자체가 막혀 있어요.
                    <span className="text-warning/70"> 라운드는 그대로 진행되며 잠시 후 정답이 공개돼요.</span>
                </div>
            )}
            {isOpen && playback === "blocked" && (
                <div className="px-3 py-2 rounded-xl bg-zinc-900/60 border border-neon-cyan/30 text-sm text-zinc-200">
                    브라우저가 자동재생을 막았어요. 화면을 눌러 소리를 켜주세요.
                </div>
            )}

        </div>
    );
}
