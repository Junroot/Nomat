import { create } from "zustand";
import { createJSONStorage, persist, type StateStorage } from "zustand/middleware";

/**
 * 앱 전역 볼륨 설정. **앱의 모든 소리 출처는 이 스토어를 따른다.**
 *
 * 현재 출처는 라운드 오디오 플레이어(`useRoundAudioOrchestrator`)와 플레이리스트 미리듣기
 * (`MusicPlayer`) 둘이다. 새 소리 출처를 만들 때는 자체 볼륨 상수를 두지 말고 이 스토어를
 * 구독해야 한다 — 출처마다 볼륨이 따로 놀면 사용자가 한 번에 줄일 방법이 없다.
 *
 * **음소거는 `volume === 0`으로 표현한다.** 별도의 음소거 플래그를 두지 않는 이유는 라운드
 * 오케스트레이터가 선버퍼링 정답 유출 방지에 플레이어 수준 `mute()`/`unMute()`를 스스로
 * 쓰기 때문이다. 사용자 음소거를 같은 수단으로 만들면 라운드 교대 시 오케스트레이터의
 * `unMute()`가 사용자 의도를 덮는다. YouTube API에서 볼륨 0은 mute 상태와 독립이라
 * (`unMute()`를 해도 볼륨 0이면 무음) 오케스트레이터가 아무것도 몰라도 된다.
 *
 * 값은 브라우저 localStorage에 영속된다(키 `nomat.volume`). 서버에는 저장하지 않는다 —
 * 볼륨은 계정이 아니라 듣는 환경의 속성이다.
 */

export const DEFAULT_VOLUME = 50;
const STORAGE_KEY = "nomat.volume";

interface VolumeState {
    /** 0~100 정수. 0이 곧 음소거다. */
    volume: number;
    /** 마지막으로 들리던(0이 아닌) 볼륨. 음소거 해제 시 복원값이며 1~100이다. */
    lastAudible: number;
    setVolume: (volume: number) => void;
    toggleMute: () => void;
}

function isIntegerInRange(value: unknown, min: number, max: number): value is number {
    return typeof value === "number" && Number.isInteger(value) && value >= min && value <= max;
}

/**
 * localStorage에서 읽은 값을 정화한다. localStorage는 사용자가 편집할 수 있어 신뢰하지 않는다 —
 * 숫자가 아니거나, 정수가 아니거나, 범위 밖이면 기본값으로 대체한다.
 */
function sanitize(persisted: unknown): Pick<VolumeState, "volume" | "lastAudible"> {
    const raw = typeof persisted === "object" && persisted !== null
        ? (persisted as Record<string, unknown>)
        : {};
    return {
        volume: isIntegerInRange(raw.volume, 0, 100) ? raw.volume : DEFAULT_VOLUME,
        lastAudible: isIntegerInRange(raw.lastAudible, 1, 100) ? raw.lastAudible : DEFAULT_VOLUME,
    };
}

/**
 * 예외를 삼키는 localStorage 래퍼.
 *
 * zustand `persist`는 스토리지 **획득**(`createJSONStorage`의 getter)과 **읽기**(hydration)의
 * 예외는 삼키지만, `set` 때마다 호출하는 `setItem`의 예외는 그대로 던진다(zustand 5.0.3
 * `persistImpl`의 `setItem`에 try/catch가 없다). 사생활 보호 모드·용량 초과·저장소 차단 환경에서
 * 슬라이더 조작이 곧 예외가 되지 않도록 여기서 막는다. 실패하면 값은 메모리에만 남고 세션 동안만
 * 유지된다 — 스펙이 허용하는 동작이다.
 */
const safeLocalStorage: StateStorage = {
    getItem: (name) => localStorage.getItem(name),
    setItem: (name, value) => {
        try {
            localStorage.setItem(name, value);
        } catch {
            // 저장 불가 — 메모리 상태로만 동작한다.
        }
    },
    removeItem: (name) => {
        try {
            localStorage.removeItem(name);
        } catch {
            // 위와 같다.
        }
    },
};

export default create<VolumeState>()(
    persist(
        (set) => ({
            volume: DEFAULT_VOLUME,
            lastAudible: DEFAULT_VOLUME,
            setVolume: (volume) => {
                const next = Math.round(Math.min(100, Math.max(0, volume)));
                // 0으로 내리는 것은 음소거다 — 복원값은 마지막으로 들리던 값으로 남긴다.
                set((state) => ({
                    volume: next,
                    lastAudible: next > 0 ? next : state.lastAudible,
                }));
            },
            toggleMute: () => set((state) => ({
                volume: state.volume > 0 ? 0 : state.lastAudible,
            })),
        }),
        {
            name: STORAGE_KEY,
            storage: createJSONStorage(() => safeLocalStorage),
            partialize: ({ volume, lastAudible }) => ({ volume, lastAudible }),
            merge: (persisted, current) => ({ ...current, ...sanitize(persisted) }),
        },
    ),
);
