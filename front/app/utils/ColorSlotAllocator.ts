/**
 * 채팅 닉네임 색 슬롯 수. 방 정원(백엔드 `Room.MAX_MAX_ENTRIES_COUNT = 20`)과 같아
 * 정원 안에서는 슬롯이 고갈되지 않는다.
 *
 * 정원 상한을 올리면 이 값과 `app.css`의 `--color-chat-*` 토큰, `ChatMessageList`의
 * 클래스 배열을 함께 늘려야 한다. 팔레트만 그대로 두면 아래 폴백이 겹침을 감수하게 된다.
 */
export const CHAT_COLOR_SLOT_COUNT = 20;

/**
 * 방 세션 동안 playerId → 색 슬롯 대응을 관리하는 순수 할당기. React 의존 없음.
 *
 * 규칙(design.md Decision 2):
 *   - 처음 본 playerId → 비어 있는 가장 낮은 슬롯
 *   - 이미 아는 playerId → 기존 슬롯 그대로 (입퇴장이 남은 사람의 색을 흔들지 않는다)
 *   - release → 슬롯을 비워 다음 입장자가 재사용
 *
 * "가장 낮은 빈 슬롯"인 이유는 팔레트가 앞 슬롯일수록 서로 먼 색으로 정렬되어 있어서다.
 */
export default class ColorSlotAllocator {
    private readonly slotByPlayer = new Map<number, number>();

    /** 이미 배정된 참가자면 기존 슬롯, 아니면 가장 낮은 빈 슬롯을 배정해 돌려준다. */
    assign(playerId: number): number {
        const existing = this.slotByPlayer.get(playerId);
        if (existing !== undefined) return existing;

        const slot = this.lowestFreeSlot() ?? this.fallbackSlot(playerId);
        this.slotByPlayer.set(playerId, slot);
        return slot;
    }

    /** 배정을 해제한다. 모르는 playerId면 아무 일도 하지 않는다. */
    release(playerId: number): void {
        this.slotByPlayer.delete(playerId);
    }

    has(playerId: number): boolean {
        return this.slotByPlayer.has(playerId);
    }

    private lowestFreeSlot(): number | undefined {
        const taken = new Set(this.slotByPlayer.values());
        for (let slot = 0; slot < CHAT_COLOR_SLOT_COUNT; slot++) {
            if (!taken.has(slot)) return slot;
        }
        return undefined;
    }

    /**
     * 빈 슬롯이 없을 때의 폴백. 정원 = 슬롯 수라 현재는 도달하지 않지만, 어떤 경우에도
     * 색 없는 닉네임이 나오지 않도록 겹침을 감수하고 나머지 연산으로 떨어뜨린다.
     */
    private fallbackSlot(playerId: number): number {
        return playerId % CHAT_COLOR_SLOT_COUNT;
    }
}
