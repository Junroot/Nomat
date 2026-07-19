import { useEffect, useState } from "react";

/**
 * 표시용 카운트다운. `deadlineAt`(epoch ms)까지 남은 밀리초를 반환한다.
 *
 * 순수 리듀서 밖의 표시 로직 — 여기서만 `Date.now()`를 읽는다. 남은 시간이 0에 도달해도
 * 라운드를 종료하지 않는다(권위는 서버 REVEAL). clock skew는 화면 표시만 어긋난다.
 *
 * @returns 남은 ms(0 이상), `deadlineAt`이 null이면 null.
 */
export default function useCountdown(deadlineAt: number | null): number | null {
    const [remaining, setRemaining] = useState<number | null>(
        deadlineAt == null ? null : Math.max(0, deadlineAt - Date.now()),
    );

    useEffect(() => {
        if (deadlineAt == null) {
            setRemaining(null);
            return;
        }
        const tick = () => setRemaining(Math.max(0, deadlineAt - Date.now()));
        tick();
        const id = setInterval(tick, 250);
        return () => clearInterval(id);
    }, [deadlineAt]);

    return remaining;
}
