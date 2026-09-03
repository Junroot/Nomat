import PlayIcon from "~/assets/play-circle.svg?react";

interface AudioGateOverlayProps {
    onArm: () => void;
}

/**
 * 오디오 자동재생 제스처 게이트. `PLAYING` 진입·재접속 시 arming 전에 1회 표시된다.
 * 클릭이 곧 사용자 제스처가 되어 브라우저 자동재생 정책을 통과시키고, 이후 라운드는 조용히 자동재생된다.
 * 음소거 자동재생은 노래 맞히기에 부적합하므로 제스처가 필수다.
 */
export default function AudioGateOverlay({ onArm }: AudioGateOverlayProps) {
    return (
        <div className="fixed inset-0 z-50 flex flex-col items-center justify-center gap-6 bg-zinc-950/80 backdrop-blur-sm">
            <p className="text-2xl font-bold text-zinc-100">게임이 시작됐어요</p>
            <p className="text-sm text-zinc-400">소리를 켜고 노래를 맞혀보세요.</p>
            {/* 이 오버레이는 `PLAYING` 진입 시 전원이 반드시 통과하므로 조작 안내를 붙일 자리로 공짜다.
                단축키는 조합키가 있는 화면 폭에서만 사실이라 반응형으로 갈라 쓴다. */}
            <div className="flex flex-col items-center gap-1 text-xs text-zinc-500">
                <p>정답은 채팅으로 입력하세요</p>
                <p className="hidden md:block">모르겠는 곡은 Shift+Enter</p>
                <p className="md:hidden">모르겠는 곡은 🤔 버튼</p>
            </div>
            <button
                type="button"
                className="inline-flex items-center gap-2 px-8 py-3 rounded-2xl bg-neon-cyan/20 text-neon-cyan font-semibold text-lg hover:bg-neon-cyan/30 transition-colors cursor-pointer"
                onClick={onArm}
            >
                <PlayIcon className="size-6" />
                게임 참여 · 소리 켜기
            </button>
        </div>
    );
}
