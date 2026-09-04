import { useEffect, useState, type FocusEvent } from "react";
import VolumeUpIcon from "~/assets/volume-up.svg?react";
import VolumeOffIcon from "~/assets/volume-off.svg?react";
import useVolumeStore from "~/stores/VolumeStore";

/**
 * 앱 전역 볼륨 컨트롤. 네비게이션 레일(72px) 하단에 아이콘 하나로 앉고, **마우스를 올리면**
 * 오른쪽으로 슬라이더 팝오버가 펼쳐진다. 아이콘 클릭은 음소거 토글이다 — YouTube·Spotify의
 * 스피커 아이콘과 같은 모델이라 학습 비용이 없다. 레일이 `hidden md:flex`라 모바일에서는 이
 * 컴포넌트도 함께 사라진다 — 별도의 모바일 분기는 두지 않는다(모바일은 기기 볼륨에 위임한다).
 *
 * 값은 `VolumeStore` 하나가 소유하고 이 컴포넌트는 그것을 그리고 바꿀 뿐이다. 소리 출처가
 * 있든 없든 항상 표시한다 — OS 볼륨 아이콘이 소리 안 날 때 사라지지 않는 것과 같다.
 *
 * 키보드: 아이콘에 포커스가 들어오면 펼쳐지고 Tab으로 슬라이더에 닿는다(방향키로 조절).
 * 포커스가 컨트롤 밖으로 나가거나 `Escape`를 누르면 접힌다. `Escape` 외의 키는 건드리지
 * 않으므로 채팅 입력창의 `Enter`/`Shift+Enter` 처리와 간섭하지 않는다.
 */
export default function VolumeControl() {
    const volume = useVolumeStore((state) => state.volume);
    const setVolume = useVolumeStore((state) => state.setVolume);
    const toggleMute = useVolumeStore((state) => state.toggleMute);
    const [open, setOpen] = useState(false);

    const muted = volume === 0;
    const Icon = muted ? VolumeOffIcon : VolumeUpIcon;

    useEffect(() => {
        if (!open) {
            return;
        }
        const handleKeyDown = (e: KeyboardEvent) => {
            if (e.key === "Escape") {
                setOpen(false);
            }
        };
        document.addEventListener("keydown", handleKeyDown);
        return () => document.removeEventListener("keydown", handleKeyDown);
    }, [open]);

    // 포커스가 컨트롤(아이콘·슬라이더) 밖으로 나갈 때만 접는다.
    const handleBlur = (e: FocusEvent<HTMLDivElement>) => {
        if (!e.currentTarget.contains(e.relatedTarget as Node | null)) {
            setOpen(false);
        }
    };

    return (
        <div
            className="relative w-[72px] flex justify-center"
            onMouseEnter={() => setOpen(true)}
            onMouseLeave={() => setOpen(false)}
            onFocus={() => setOpen(true)}
            onBlur={handleBlur}
        >
            <button
                type="button"
                aria-label={muted ? "음소거 해제" : "음소거"}
                aria-pressed={muted}
                title={muted ? "음소거 해제" : `볼륨 ${volume} · 클릭하면 음소거`}
                onClick={toggleMute}
                className={`size-[40px] flex items-center justify-center rounded-lg transition-colors cursor-pointer ${
                    open ? "bg-zinc-800 text-neon-cyan" : "text-zinc-400 hover:text-zinc-200"
                }`}
            >
                <Icon className="size-[24px]" />
            </button>
            {/* 팝오버 래퍼는 아이콘과의 간격(`pl-2`)까지 자기 영역으로 삼는다 — 간격이 margin이면
                마우스가 그 틈을 지나는 순간 mouseleave가 나서 팝오버가 닫힌다. */}
            {open && (
                <div className="absolute left-full bottom-0 pl-2 z-50">
                    <div
                        role="group"
                        aria-label="볼륨 조절"
                        className="flex items-center gap-3 px-3 py-2 rounded-xl bg-card border border-border shadow-lg animate-fade-in"
                    >
                        {/* 네이티브 range — 방향키 조작·접근성을 공짜로 얻는다.
                            트랙은 zinc(`--color-muted`), 채움과 썸은 `neon-cyan`. 채움은 값에 따라 그라디언트
                            경계를 옮겨 그린다(CSS만으로 채움 구간을 표현할 표준 방법이 없다). */}
                        <input
                            type="range"
                            min={0}
                            max={100}
                            step={1}
                            value={volume}
                            aria-label="볼륨"
                            aria-valuenow={volume}
                            onChange={(e) => setVolume(Number(e.target.value))}
                            style={{
                                background: `linear-gradient(to right, var(--color-neon-cyan) ${volume}%, var(--color-muted) ${volume}%)`,
                            }}
                            className="w-32 h-1 rounded-full appearance-none cursor-pointer outline-none
                                focus-visible:ring-2 focus-visible:ring-neon-cyan/50
                                [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:size-3.5
                                [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-neon-cyan
                                [&::-webkit-slider-thumb]:shadow-[0_0_8px_rgba(34,211,238,0.5)]
                                [&::-moz-range-thumb]:size-3.5 [&::-moz-range-thumb]:rounded-full
                                [&::-moz-range-thumb]:border-0 [&::-moz-range-thumb]:bg-neon-cyan"
                        />
                        <span className="w-7 text-right text-xs tabular-nums text-zinc-400">{volume}</span>
                    </div>
                </div>
            )}
        </div>
    );
}
