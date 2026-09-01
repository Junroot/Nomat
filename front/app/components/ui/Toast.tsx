import { useEffect, useRef } from "react";
import { Toaster as SonnerToaster } from "sonner";
import "sonner/dist/styles.css";
import { MODAL_PROMOTED_EVENT } from "~/components/ui/Modal";

/**
 * 전역 토스트 컨테이너.
 *
 * `<dialog>`가 `showModal()`로 top layer에 올라가면 일반 레이어의 토스트는 `z-index`와
 * 무관하게 `::backdrop` 아래로 내려간다. 그래서 토스트도 popover로 top layer에 올린다.
 * top layer는 승격 **순서**로 쌓이므로 모달이 열릴 때마다(`MODAL_PROMOTED_EVENT`)
 * `hidePopover()` → `showPopover()`로 다시 승격한다.
 *
 * Popover 미지원 브라우저에서는 승격을 통째로 건너뛰고 종전대로 일반 레이어에 렌더한다
 * (이 컴포넌트는 앱 셸에 상시 렌더되므로 예외 하나가 화면 전체를 날린다).
 */
export default function Toaster() {
    const containerRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const container = containerRef.current;
        if (!container || typeof container.showPopover !== "function") return;

        function promote(target: HTMLDivElement) {
            try {
                if (target.matches(":popover-open")) target.hidePopover();
                target.showPopover();
                return true;
            } catch {
                return false;
            }
        }

        container.setAttribute("popover", "manual");
        if (!promote(container)) {
            container.removeAttribute("popover");
            return;
        }

        function handleModalPromoted() {
            if (!container) return;
            if (!promote(container)) container.removeAttribute("popover");
        }

        window.addEventListener(MODAL_PROMOTED_EVENT, handleModalPromoted);
        return () => {
            window.removeEventListener(MODAL_PROMOTED_EVENT, handleModalPromoted);
            try {
                if (container.matches(":popover-open")) container.hidePopover();
            } catch {
                // 정리 실패는 무시한다
            }
            container.removeAttribute("popover");
        };
    }, []);

    return (
        <div ref={containerRef} className="toast-top-layer">
            <SonnerToaster
                position="bottom-center"
                duration={3000}
                toastOptions={{
                    style: {
                        background: "var(--color-surface)",
                        border: "1px solid var(--color-border)",
                        color: "#fafafa",
                    },
                    classNames: {
                        success: "!border-emerald-500/30",
                        error: "!border-red-500/30",
                        info: "!border-cyan-500/30",
                        warning: "!border-amber-500/30",
                    },
                }}
            />
        </div>
    );
}
