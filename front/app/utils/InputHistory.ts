/**
 * 채팅 입력창의 전송 이력 상한. 라운드 수 × 한 라운드의 추측 횟수를 곱해도 넉넉하고,
 * 200자 × 50이라 메모리는 논외다. 넘치면 가장 오래된 것부터 버린다.
 */
export const INPUT_HISTORY_LIMIT = 50;

/**
 * 셸의 명령 이력처럼 "내가 보낸 것"을 방향키로 되짚는 순수 모델. React 의존 없음.
 *
 * 상태(design.md Decision 2):
 *
 *   entries  [ h0  h1  h2 ]          cursor = 3 (= entries.length, 탐색 중 아님 = draft 자리)
 *
 *   "abc" 타이핑 ─ ↑ ──▶ draft="abc", cursor=2, 표시 h2
 *                  ↑ ──▶ cursor=1, 표시 h1
 *                  ↑ ──▶ cursor=0, 표시 h0
 *                  ↑ ──▶ (null) 그대로 h0
 *                  ↓ ──▶ cursor=1, 표시 h1
 *                  ↓ ──▶ cursor=2, 표시 h2
 *                  ↓ ──▶ cursor=3, 표시 "abc"   ← draft 복원
 *                  ↓ ──▶ (null) 그대로
 *
 * 규칙:
 *   - 연속 중복만 접는다(셸 `ignoredups`). 전체 중복을 지우면(`erasedups`) 시간 순서가
 *     흐트러져 "A, B, A"를 보낸 사람이 ↑↑로 B를 기대하는데 A가 앞으로 옮겨진다(Decision 4).
 *   - 항목은 불변이다. 탐색 중 편집은 탐색을 끝낼 뿐 항목을 고치지 않는다 — 핵심 용례가
 *     "직전 추측 불러와 → 살짝 고쳐 → 전송"이고 고친 것은 전송되며 그때 새 항목이 된다(Decision 3).
 *   - 상한을 넘으면 가장 오래된 것부터 버린다. `push`는 전송 시점에만 불리고 커서를 끝으로
 *     되돌리므로, 절삭이 탐색 중인 커서를 어긋나게 하는 경로는 없다.
 */
export default class InputHistory {
    private readonly entries: string[] = [];
    /** `entries.length`면 탐색 중이 아니다(draft 자리). */
    private cursor = 0;
    /** 탐색을 시작한 순간 입력창에 있던 미전송 텍스트. */
    private draft = "";

    constructor(private readonly limit: number = INPUT_HISTORY_LIMIT) {}

    /**
     * 전송한 내용을 이력 끝에 넣고 탐색 상태를 초기화한다.
     * 직전 항목과 같으면 새 항목을 만들지 않는다(연속 중복 접기).
     */
    push(content: string): void {
        if (this.entries[this.entries.length - 1] !== content) {
            this.entries.push(content);
            if (this.entries.length > this.limit) {
                this.entries.splice(0, this.entries.length - this.limit);
            }
        }
        this.cursor = this.entries.length;
        this.draft = "";
    }

    /**
     * 한 칸 과거로. 탐색을 시작하는 호출이면 `current`를 draft로 보관한다.
     * 이력이 비었거나 가장 오래된 항목이면 `null`(입력값을 바꾸지 않는다).
     */
    prev(current: string): string | null {
        if (!this.isBrowsing()) this.draft = current;
        if (this.cursor === 0) return null;
        this.cursor -= 1;
        return this.entries[this.cursor];
    }

    /**
     * 한 칸 현재로. 이력 끝에 닿으면 draft를 돌려준다.
     * 탐색 중이 아니면 `null`(입력값을 바꾸지 않는다).
     */
    next(): string | null {
        if (!this.isBrowsing()) return null;
        this.cursor += 1;
        return this.isBrowsing() ? this.entries[this.cursor] : this.draft;
    }

    /**
     * 탐색을 끝낸다(사용자가 타이핑을 시작했을 때). draft는 지우지 않는다 — 다음 `prev`가
     * 그 시점의 입력값으로 다시 잡는다.
     */
    exitBrowsing(): void {
        this.cursor = this.entries.length;
    }

    private isBrowsing(): boolean {
        return this.cursor < this.entries.length;
    }
}
