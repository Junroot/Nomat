import { useCallback, useEffect, useRef, useState } from "react";

/**
 * 클립 재생 상태.
 * - `idle`      재생 시도 전이거나, 아직 판정이 서지 않음(버퍼링 생존 신호로 판정이 해제된 경우 포함)
 * - `playing`   정상 재생 중
 * - `blocked`   브라우저 자동재생 정책에 막힘 — 재생 불가가 아니라 제스처 재유도 대상
 * - `unplayable` 이 트랙이 임베드에서 재생되지 않음(콘텐츠 경고·임베드 차단 등)
 */
export type ClipPlaybackStatus = "idle" | "playing" | "blocked" | "unplayable";

/**
 * 재생 불가 판정까지 기다리는 시간. "재생 완료까지의 여유"가 아니라
 * **플레이어가 살아있다는 신호(BUFFERING)가 오기까지의 여유**다.
 */
export const PLAYBACK_VERDICT_MS = 3000;

const YT_STATE_PLAYING = 1;
const YT_STATE_BUFFERING = 3;

interface UseClipPlaybackResult {
    status: ClipPlaybackStatus;
    /** YouTube `onReady`의 player(=`event.target`)를 넘긴다. */
    handleReady: (player: any) => void;
    /** YouTube `onStateChange`의 state(=`event.data`)를 넘긴다. */
    handleStateChange: (state: number) => void;
    /** YouTube `onError` 발생 시 호출한다. */
    handleError: () => void;
}

/**
 * 클립이 실제로 재생되기 시작했는지 관찰해 재생 불가를 판정하는 훅.
 *
 * 일부 YouTube 트랙(`CONTENT_CHECK_REQUIRED` 등)은 임베드에서 재생이 거부되는데
 * 이때 `onError`가 오는지는 보장되지 않는다. 그래서 오류 이벤트만 믿지 않고
 * **재생 개시 관찰**로 판정한다:
 *
 * - `onError`         → 즉시 `unplayable`
 * - `onAutoplayBlocked` → `blocked` (재생 불가가 아니라 게이트 문제)
 * - state 3(BUFFERING) → 플레이어 생존 신호 → **판정 해제**(느린 네트워크 오탐 방지)
 * - state 1(PLAYING)   → `playing`
 * - 위 어느 것도 없이 {@link PLAYBACK_VERDICT_MS} 경과 → `unplayable`
 *
 * 판정은 라운드마다 새로 서야 한다. 플레이어를 방 세션 동안 재사용하게 되면서
 * 리마운트가 공짜로 주던 초기화가 사라졌으므로, `roundNumber`를 받아 **훅이 직접 재무장한다.**
 * ⚠️ `roundSeq`(전이 epoch)가 아니다 — 그 값은 REVEAL 진입에서도 바뀌어 판정이 헛돈다.
 */
export default function useClipPlayback(armed: boolean, roundNumber: number): UseClipPlaybackResult {
    const [status, setStatus] = useState<ClipPlaybackStatus>("idle");
    // 판정이 끝났는지(확정되었거나 생존 신호로 해제됨). true면 타이머를 다시 걸지 않는다.
    const decidedRef = useRef(false);
    const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const playerReadyRef = useRef(false);

    const clearTimer = useCallback(() => {
        if (timerRef.current != null) {
            clearTimeout(timerRef.current);
            timerRef.current = null;
        }
    }, []);

    // 재생 시도를 관찰하기 시작한다. 이미 판정이 끝났거나 관찰 중이면 무시한다.
    const startWatching = useCallback(() => {
        if (decidedRef.current || timerRef.current != null) {
            return;
        }
        timerRef.current = setTimeout(() => {
            timerRef.current = null;
            if (decidedRef.current) {
                return;
            }
            decidedRef.current = true;
            setStatus("unplayable");
        }, PLAYBACK_VERDICT_MS);
    }, []);

    const handleReady = useCallback(
        (player: any) => {
            playerReadyRef.current = true;
            // 자동재생 차단 통지는 youtube-player가 프록시하지 않으므로 실제 플레이어에 직접 건다.
            // IFrame API가 이 이벤트를 지원하지 않는 환경에서는 호출이 실패하거나 영영 발생하지 않으며,
            // 그 경우에도 아래 재생 개시 관찰이 판정을 대신한다.
            try {
                player.addEventListener?.("onAutoplayBlocked", () => {
                    decidedRef.current = true;
                    clearTimer();
                    setStatus("blocked");
                });
            } catch {
                // 지원하지 않는 IFrame API 버전 — 관찰 경로로 폴백한다.
            }
            if (armed) {
                startWatching();
            }
        },
        [armed, clearTimer, startWatching],
    );

    const handleStateChange = useCallback(
        (state: number) => {
            if (state === YT_STATE_PLAYING) {
                decidedRef.current = true;
                clearTimer();
                setStatus("playing");
                return;
            }
            if (state === YT_STATE_BUFFERING) {
                // 플레이어가 살아있다 — 타임아웃 판정을 해제한다. 재생이 늦어도 재생 불가가 아니다.
                decidedRef.current = true;
                clearTimer();
            }
        },
        [clearTimer],
    );

    const handleError = useCallback(() => {
        decidedRef.current = true;
        clearTimer();
        setStatus("unplayable");
    }, [clearTimer]);

    // 라운드 도중 arming되면(플레이어가 이미 ready인 상태) 그때부터 관찰을 시작한다.
    useEffect(() => {
        if (armed && playerReadyRef.current) {
            startWatching();
        }
    }, [armed, startWatching]);

    // 라운드 경계에서 판정을 새로 세운다 — 이전 트랙의 판정이 새 트랙으로 이월되면 안 된다.
    // 이 이펙트는 호출부(RoundAudioPlayer)의 트랙 교체 이펙트보다 훅 순서상 먼저 돌므로,
    // 새 트랙이 적재되기 전에 관찰이 재무장된다.
    const watchedRoundRef = useRef(roundNumber);
    useEffect(() => {
        if (watchedRoundRef.current === roundNumber) {
            return;
        }
        watchedRoundRef.current = roundNumber;
        decidedRef.current = false;
        clearTimer();
        setStatus("idle");
        if (armed && playerReadyRef.current) {
            startWatching();
        }
    }, [roundNumber, armed, clearTimer, startWatching]);

    useEffect(() => clearTimer, [clearTimer]);

    return { status, handleReady, handleStateChange, handleError };
}
