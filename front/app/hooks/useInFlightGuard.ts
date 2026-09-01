import { useCallback, useMemo, useRef } from "react";

export interface InFlightGuard {
    /** 요청 직전에 호출해 그 시점의 세대 토큰을 잡는다. */
    begin: () => number;
    /** 결과가 돌아왔을 때 호출. false면 그 사이 `invalidate()`가 불린 것이다. */
    isCurrent: (token: number) => boolean;
    /** 진행 중인 요청을 무효화한다(모달이 닫히는 시점 등). */
    invalidate: () => void;
}

/**
 * 모달이 닫힌 뒤 도착한 비동기 결과가 부수 효과를 일으키지 않게 막는 세대 가드.
 *
 * 닫기 억제(`Modal`의 `dismissible`)는 **최선 노력**이다 — `<dialog>`는 close watcher
 * 남용 방지 규칙 때문에 플랫폼이 강제로 닫는 경로가 실재한다. 그래서 "작업 중에는 닫히지
 * 않는다"에 기대어 뒷정리를 미룰 수 없고, 닫힌 뒤 도착한 결과를 무시할 장치가 따로 필요하다.
 *
 * ```ts
 * const guard = useInFlightGuard();
 * const token = guard.begin();
 * const result = await request();
 * if (!guard.isCurrent(token)) return;  // 그 사이 닫혔다 — 화면 이동 등을 건너뛴다
 * ```
 *
 * 무효화 시점(닫기 핸들러에서 명령형으로 vs `isOpen` 변화 이펙트에서)은 소비자가 정한다.
 */
export default function useInFlightGuard(): InFlightGuard {
    const generationRef = useRef(0);

    const begin = useCallback(() => generationRef.current, []);
    const isCurrent = useCallback((token: number) => token === generationRef.current, []);
    const invalidate = useCallback(() => {
        generationRef.current += 1;
    }, []);

    return useMemo(() => ({ begin, isCurrent, invalidate }), [begin, isCurrent, invalidate]);
}
