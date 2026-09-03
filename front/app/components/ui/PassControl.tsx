interface PassControlProps {
    passedCount: number;
    requiredCount: number;
    passed: boolean;
    onToggle: () => void;
}

/**
 * `OPEN` 구간의 포기("모르겠어요") 컨트롤 — 버튼·카운터·단축키 안내를 하나가 겸한다.
 *
 * **아무도 누르지 않았을 때도 표시한다.** 현황 표시에만 의존하면 "아무도 안 누름 → 카운트가 안 뜸 →
 * 기능을 발견 못 함 → 아무도 안 누름"의 닭과 달걀에 빠진다. 발견성이 필요한 순간은 정확히
 * 아무도 안 눌렀을 때이므로 어포던스가 상시 존재해야 한다.
 *
 * 카운트는 **덧붙는** 형태(`모르겠어요` → `3/4 모르겠어요`)라 켜져도 레이아웃이 밀리지 않고,
 * 토글 상태(🤔 ↔ ✓)가 같은 자리에서 바뀌어 "내가 눌렀나"가 항상 명확하다.
 *
 * 시각 강도는 낮게 유지한다 — 피하려는 것은 크기가 아니라 *유혹*이다. glow·펄스가 들어간
 * 「SKIP」 버튼은 습관적 클릭을 부른다. 조용한 컨트롤이면 읽히면서도 조르지 않는다.
 *
 * 다만 **눌린다는 신호까지 지우지는 않는다.** 상시 표시를 택한 이유가 발견성인데 클릭 가능한
 * 것으로 읽히지 않으면 그 이유가 반쯤 무효가 된다 — 특히 모바일에서는 단축키 칩이 숨겨져
 * 이 컨트롤이 유일한 경로다. 유혹을 만드는 것은 채도·모션이지 어포던스가 아니므로,
 * 채도 0인 zinc 테두리로 형태만 주고 accent 색·glow·펄스는 쓰지 않는다.
 *
 * **배치는 호출자가 소유한다.** 이 컴포넌트는 버튼만 그린다 — 채팅 피드 위에 떠 있어야 해서
 * 위치 지정이 피드 컨테이너와 한 몸이기 때문이다. 자기 자리를 스스로 잡으면 `Column2`의
 * `gap-4`가 위아래로 붙어 라운드마다 피드가 70px씩 튄다.
 */
export default function PassControl({ passedCount, requiredCount, passed, onToggle }: PassControlProps) {
    const hasCount = passedCount > 0 && requiredCount > 0;

    return (
        <button
            type="button"
            onClick={onToggle}
            aria-pressed={passed}
            className={
                // py-2는 터치 타깃 확보용이다. 데스크톱은 Shift+Enter가 주 경로지만
                // 모바일에는 이 버튼뿐이라 text-xs 한 줄 높이로는 누르기 어렵다.
                //
                // 배경은 투명이 아니라 반투명 + blur다 — 스크롤되는 채팅 피드 위에 떠 있어서
                // 투명하면 글자가 비쳐 읽히지 않는다.
                "inline-flex items-center gap-1.5 px-3 py-2 text-xs rounded-full border transition-colors cursor-pointer " +
                "backdrop-blur-sm " +
                (passed
                    ? "border-zinc-600 bg-zinc-800 text-zinc-300"
                    : "border-zinc-700 bg-zinc-900/80 text-zinc-500 hover:border-zinc-600 hover:text-zinc-300")
            }
        >
            <span aria-hidden="true">{passed ? "✓" : "🤔"}</span>
            {hasCount && <span className="tabular-nums">{passedCount}/{requiredCount}</span>}
            <span>모르겠어요</span>
            {/* 조합키가 없는 화면 폭에서 "Shift+Enter"는 사실이 아니다. */}
            <span className="hidden md:inline text-zinc-600">Shift+Enter</span>
        </button>
    );
}
