import { useCallback, useEffect, useRef } from "react";

/**
 * 바닥에 "붙어 있다"고 볼 여유(px). 이보다 가까우면 최신 내용을 보고 있는 것으로 판정한다.
 * 스크롤을 끝까지 내려도 소수점 오차로 0이 되지 않는 경우가 있어 여유가 필요하다.
 */
const NEAR_BOTTOM_THRESHOLD_PX = 80;

/**
 * 스크롤 컨테이너를 바닥에 붙여 두는 훅. 채팅처럼 아래로 자라는 목록에 쓴다.
 *
 * 두 가지를 함께 본다:
 *
 * 1. **내용이 늘어날 때**(`dependency` 변화) — 새 메시지가 오면 부드럽게 따라 내려간다.
 * 2. **컨테이너 크기가 변할 때**(`ResizeObserver`) — 위쪽 영역이 커져 목록이 짧아지면
 *    `clientHeight`만 줄고 `scrollTop`은 브라우저가 그대로 두므로, 바닥에 붙어 있던 사람이
 *    **줄어든 높이만큼 위**를 보게 된다. 즉 방금 도착한 내용이 화면 밖으로 밀려난다.
 *    브라우저의 scroll anchoring은 컨테이너 *안쪽* 콘텐츠 변화를 보정하는 기능이라
 *    컨테이너 자체의 리사이즈는 커버하지 않는다 — 명시적으로 처리해야 한다.
 *
 * 2번의 트리거를 **크기**로 잡은 것이 핵심이다. 원인은 특정 화면 단계가 아니라 높이 변화이므로,
 * 라운드 정보 영역이 커지는 경우·영상이 나타나는 경우·모바일 가상 키보드가 올라오는 경우가
 * 한 규칙으로 처리되고, 나중에 위쪽에 무엇을 더해도 이 로직을 다시 손댈 필요가 없다.
 *
 * 어느 경우든 **스스로 위로 스크롤해 과거를 보고 있던 사용자는 끌어내리지 않는다.**
 *
 * 콜백 안의 스크롤 조작이 다시 리사이즈를 부르지 않는다 — 스크롤 위치 변경은 컨테이너 크기를
 * 바꾸지 않는다. 그럼에도 바닥 근처일 때만 조작하므로 루프가 생겨도 수렴한다.
 *
 * @param dependency 내용이 바뀌었음을 알리는 값(예: 메시지 배열). 바뀔 때마다 바닥 추종을 시도한다.
 * @returns `containerRef`(스크롤 컨테이너에 붙일 ref), `endRef`(목록 끝 앵커), `onScroll`(위치 추적 핸들러).
 */
export default function useStickToBottom(dependency: unknown) {
    const containerElRef = useRef<HTMLDivElement | null>(null);
    const observerRef = useRef<ResizeObserver | null>(null);
    const endRef = useRef<HTMLDivElement>(null);
    const isNearBottomRef = useRef(true);

    // 관찰 시작을 이펙트가 아니라 ref 콜백에서 한다 — 컨테이너는 로딩이 끝난 뒤에야
    // 마운트되므로, 빈 의존성 이펙트로는 그 시점을 놓친다.
    const containerRef = useCallback((el: HTMLDivElement | null) => {
        observerRef.current?.disconnect();
        observerRef.current = null;
        containerElRef.current = el;
        if (!el) return;

        const observer = new ResizeObserver(() => {
            if (!isNearBottomRef.current) return;
            // 리사이즈 보정은 즉시 붙인다 — 부드럽게 움직이면 그 사이가 어긋난 화면이다.
            el.scrollTop = el.scrollHeight;
        });
        observer.observe(el);
        observerRef.current = observer;
    }, []);

    const onScroll = useCallback(() => {
        const el = containerElRef.current;
        if (!el) return;
        isNearBottomRef.current =
            el.scrollHeight - el.scrollTop - el.clientHeight < NEAR_BOTTOM_THRESHOLD_PX;
    }, []);

    useEffect(() => {
        if (isNearBottomRef.current) {
            endRef.current?.scrollIntoView({ behavior: "smooth" });
        }
    }, [dependency]);

    return { containerRef, endRef, onScroll };
}
