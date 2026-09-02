import {
    type AnimationEvent,
    type ReactNode,
    type RefObject,
    type SyntheticEvent,
    createContext,
    useContext,
    useEffect,
    useId,
    useRef,
    useState,
} from "react";

/**
 * `phase`는 **열림 여부를 결정하지 않는다.** 열림의 유일한 진실은 부모의 `isOpen`이고,
 * `phase`는 퇴장 애니메이션 동안 DOM을 유지하기 위한 파생 상태일 뿐이다.
 */
type Phase = "closed" | "open" | "closing";

/** 퇴장 애니메이션 길이(0.2s) + 여유. `animationend`가 오지 않는 경우의 폴백 상한. */
const EXIT_FALLBACK_MS = 400;

/**
 * 모달이 top layer로 승격될 때마다 발행하는 신호.
 *
 * top layer는 `z-index`가 아니라 승격 **순서**로 쌓이므로, 모달 위에 떠야 하는
 * 전역 오버레이(토스트)는 이 신호를 받아 자신을 다시 승격해야 한다.
 */
export const MODAL_PROMOTED_EVENT = "nomat:modal-promoted";

/**
 * 배경 스크롤 잠금 — 모듈 수준 참조 카운터.
 * 모달이 중첩·동시에 열려도 마지막 하나가 닫힐 때만 원래 값을 복원한다.
 */
let scrollLockCount = 0;
let previousBodyOverflow = "";

function lockBodyScroll() {
    if (scrollLockCount === 0) {
        previousBodyOverflow = document.body.style.overflow;
        document.body.style.overflow = "hidden";
    }
    scrollLockCount += 1;
}

function unlockBodyScroll() {
    if (scrollLockCount === 0) return;
    scrollLockCount -= 1;
    if (scrollLockCount === 0) {
        document.body.style.overflow = previousBodyOverflow;
    }
}

const ModalTitleIdContext = createContext<string | undefined>(undefined);

interface ModalTitleProps {
    children: ReactNode;
    className?: string;
}

/**
 * 모달의 접근 가능한 이름을 제공하는 제목.
 *
 * `Modal`이 만든 id를 받아 `<h2 id>`로 렌더하고, `<dialog aria-labelledby>`가 이를 가리킨다.
 * 표시 제목과 접근 이름이 구조적으로 어긋날 수 없도록 문자열 prop 대신 컴포넌트로 둔다.
 */
export function ModalTitle({ children, className = "" }: ModalTitleProps) {
    const titleId = useContext(ModalTitleIdContext);

    return (
        <h2 id={titleId} className={`text-xl font-bold text-zinc-100 mb-4 ${className}`}>
            {children}
        </h2>
    );
}

interface ModalProps {
    /** 열림 여부의 **유일한 진실**. 모달은 이 값을 스스로 바꾸지 않는다. */
    isOpen: boolean;
    /**
     * 닫기 신호. 부모는 이를 받으면 **무조건 이행해 `isOpen`을 `false`로 내려야 한다.**
     *
     * 사용자 조작뿐 아니라 **브라우저가 대화 상자를 강제로 닫은 경우에도** 발생한다
     * (close watcher 남용 방지 규칙 — 활성화 만료 후 첫 ESC, 연속 두 번째 ESC).
     * 부모가 이를 무시하면 `isOpen`과 실제 표시 상태가 어긋나 모달을 다시 열 수 없게 된다.
     *
     * 닫기를 막고 싶다면 `onClose`를 no-op으로 두지 말고 `dismissible={false}`를 쓴다.
     */
    onClose: () => void;
    /**
     * 사용자의 닫기 요청(배경 클릭·ESC)을 부모에게 올릴지 여부. 기본 `true`.
     *
     * `false`면 배경 클릭은 확실히 무시되지만, **ESC 억제는 최선 노력이다** —
     * 활성화가 없으면 플랫폼이 `cancel` 없이 대화 상자를 닫아버린다. 그때는 `onClose`가
     * 호출되어 상태만 동기화된다.
     */
    dismissible?: boolean;
    /**
     * `showModal()` 직후 포커스를 줄 요소. 지정하지 않으면 브라우저 기본 규칙을 따른다.
     *
     * React의 `autoFocus` prop은 `<dialog>`에서 동작하지 않는다 — React는 `autofocus`
     * 속성을 DOM에 내보내지 않고 commit 시 `focus()` 폴리필로 대체하는데, 그 시점의
     * `<dialog>`는 아직 `showModal()` 전이라 `display: none`이기 때문이다.
     */
    initialFocusRef?: RefObject<HTMLElement | null>;
    children: ReactNode;
}

/**
 * 모달 다이얼로그 프리미티브.
 *
 * **재오픈 시 상태 리셋**: 완전히 닫히면(`phase === "closed"`) children을 언마운트하므로,
 * 다시 열릴 때 children 서브트리는 새로 마운트된다. 단 이 보장은 **children 서브트리
 * 한정**이다 — 폼 state를 `Modal`보다 위에서 들고 있는 소비자는 스스로 리셋해야 한다.
 */
export default function Modal({
    isOpen,
    onClose,
    dismissible = true,
    initialFocusRef,
    children,
}: ModalProps) {
    const dialogRef = useRef<HTMLDialogElement>(null);
    const [phase, setPhase] = useState<Phase>("closed");
    /** 열림마다 증가 — 스냅백(퇴장 중 재오픈)에서도 children이 리셋되게 하는 방어적 장치 */
    const [openCount, setOpenCount] = useState(0);
    /** 부모 주도의 정상 닫힘 경로에서 발생하는 `close`인지 — `onClose` 중복 호출 가드 */
    const closingBySelfRef = useRef(false);
    const titleId = useId();

    // isOpen → phase 전이. phase는 언제 DOM에서 떼어낼지만 결정한다.
    useEffect(() => {
        if (isOpen) {
            setPhase("open");
            setOpenCount((count) => count + 1);
        } else {
            // 이미 closed면 그대로 둔다 — 강제 닫힘으로 closed가 된 뒤 부모가 isOpen을
            // 내리는 경우 다시 closing으로 되돌아가지 않도록.
            setPhase((prev) => (prev === "closed" ? prev : "closing"));
        }
    }, [isOpen]);

    // 실제 열기. StrictMode 이중 이펙트에 대비해 이미 열려 있으면 건너뛴다.
    useEffect(() => {
        if (phase !== "open") return;
        const dialog = dialogRef.current;
        if (!dialog || dialog.open) return;

        closingBySelfRef.current = false;
        dialog.showModal();
        // React autoFocus는 <dialog>에서 무효하므로 명령형으로 고정한다.
        initialFocusRef?.current?.focus();
        // top layer는 승격 순서로 쌓인다 — 모달 위에 떠야 하는 전역 오버레이에 재승격을 알린다.
        window.dispatchEvent(new CustomEvent(MODAL_PROMOTED_EVENT));
    }, [phase, initialFocusRef]);

    // 퇴장 완료 판정의 폴백. animationend와 이 타이머 중 먼저 오는 쪽이 이긴다.
    // (reduced-motion에서 animation-duration을 0으로 두거나 탭이 백그라운드로 가면
    //  animationend가 오지 않을 수 있고, 그러면 모달이 영영 언마운트되지 않는다.)
    useEffect(() => {
        if (phase !== "closing") return;
        const timer = window.setTimeout(finishExit, EXIT_FALLBACK_MS);
        return () => window.clearTimeout(timer);
    }, [phase]);

    const isMounted = phase !== "closed";
    useEffect(() => {
        if (!isMounted) return;
        lockBodyScroll();
        return unlockBodyScroll;
    }, [isMounted]);

    function finishExit() {
        const dialog = dialogRef.current;
        if (dialog?.open) {
            closingBySelfRef.current = true;
            dialog.close();
        }
        setPhase("closed");
    }

    function handleAnimationEnd(e: AnimationEvent<HTMLDivElement>) {
        if (phase !== "closing") return;
        if (e.currentTarget !== e.target) return;
        finishExit();
    }

    /** ESC. 억제는 최선 노력 — 막지 못한 경우는 `close`에서 상태만 동기화한다. */
    function handleCancel(e: SyntheticEvent<HTMLDialogElement>) {
        e.preventDefault();
        if (dismissible) onClose();
    }

    /**
     * 대화 상자가 실제로 닫혔다는 권위 있는 신호.
     * 브라우저가 강제로 닫은 경우에도 여기로 들어오므로 부모 `isOpen`을 동기화한다.
     */
    function handleNativeClose() {
        if (closingBySelfRef.current) {
            closingBySelfRef.current = false;
            return;
        }
        setPhase("closed");
        onClose();
    }

    /**
     * 배경 클릭. `::backdrop` 클릭은 `<dialog>` 엘리먼트를 타깃으로 잡히므로,
     * `<dialog>`에 패딩·배경·크기를 주지 않는 한 이 판정이 "배경을 눌렀다"와 정확히 일치한다.
     */
    function handleDialogClick(e: React.MouseEvent<HTMLDialogElement>) {
        if (e.target !== dialogRef.current) return;
        if (!dismissible) return;
        onClose();
    }

    if (!isMounted) return null;

    const closing = phase === "closing";

    return (
        <dialog
            ref={dialogRef}
            aria-labelledby={titleId}
            onCancel={handleCancel}
            onClose={handleNativeClose}
            onClick={handleDialogClick}
            // m-auto: Tailwind preflight의 `* { margin: 0 }`이 `dialog:modal`의 UA `margin: auto`를
            // 덮어써 다이얼로그가 좌상단에 붙으므로, 뷰포트 중앙 정렬을 직접 되돌린다.
            className={`m-auto p-0 border-0 bg-transparent text-inherit modal-backdrop ${closing ? "animate-backdrop-out" : "animate-backdrop-in"}`}
        >
            <ModalTitleIdContext.Provider value={titleId}>
                <div
                    key={openCount}
                    className={`bg-surface border border-border p-6 rounded-2xl shadow-glow-cyan max-h-[90vh] overflow-y-auto mx-4 ${closing ? "animate-scale-out" : "animate-scale-in"}`}
                    onAnimationEnd={handleAnimationEnd}
                >
                    {children}
                </div>
            </ModalTitleIdContext.Provider>
        </dialog>
    );
}
