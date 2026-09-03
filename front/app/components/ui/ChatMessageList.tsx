import { memo } from "react";
import UsersIcon from "~/assets/users.svg?react";
import useStickToBottom from "~/hooks/useStickToBottom";
import type RoomChatMessage from "~/utils/ChatMessage";
import type { SystemMessage } from "~/utils/ChatMessage";

const NEON_COLORS = [
    "text-neon-cyan",
    "text-neon-purple",
    "text-neon-pink",
    "text-neon-green",
] as const;

const SYSTEM_MESSAGE_TEXT: Record<SystemMessage["eventType"], string> = {
    join: "입장했습니다",
    leave: "퇴장했습니다",
    start: "게임을 시작했습니다",
    end: "게임을 종료했습니다",
};

function nicknameColor(senderId: number): string {
    return NEON_COLORS[senderId % NEON_COLORS.length];
}

function formatTime(timestamp: string): string {
    const d = new Date(timestamp);
    return d.toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" });
}

interface ChatMessageItemProps {
    msg: RoomChatMessage;
}

/**
 * 메시지 한 항목. `memo`라서 `msg` 참조가 같으면 렌더를 건너뛴다 — 메시지 객체는
 * `useRoomSubscription`이 만든 뒤 변경하지 않으므로 기존 항목은 새 메시지가 와도 그대로다.
 */
const ChatMessageItem = memo(function ChatMessageItem({ msg }: ChatMessageItemProps) {
    if (msg.type === "system") {
        return (
            <div className="flex justify-center py-1.5">
                <p className="text-zinc-500 text-sm">
                    {msg.targetNickname}님이 {SYSTEM_MESSAGE_TEXT[msg.eventType]}
                </p>
            </div>
        );
    }
    return (
        <div className="flex flex-row gap-2 px-2 py-1.5 hover:bg-zinc-800/50 rounded-lg transition-colors duration-200">
            <UsersIcon className="size-8 rounded-full border border-zinc-600 shrink-0 mt-0.5" />
            <div className="flex flex-col min-w-0">
                <div className="flex items-baseline gap-2">
                    <span className={`font-semibold text-sm ${nicknameColor(msg.senderId)}`}>
                        {msg.senderNickname}
                    </span>
                    <span className="text-zinc-600 text-xs">{formatTime(msg.timestamp)}</span>
                </div>
                <p className="text-zinc-200 text-sm break-words">{msg.content}</p>
            </div>
        </div>
    );
});

interface ChatMessageListProps {
    messages: RoomChatMessage[];
    // 피드 위에 떠 있는 컨트롤(포기 버튼)의 자리를 하단에 미리 확보할지. 호출자가 그 컨트롤을
    // 겹쳐 놓을 때 켠다 — 최신 메시지가 가려지지 않게 하면서, 컨트롤이 나타났다 사라져도
    // 피드 높이가 흔들리지 않도록 게임 중 내내 고정한다.
    bottomInset?: boolean;
}

/**
 * 채팅 피드. 스크롤 컨테이너와 바닥 추종(`useStickToBottom`)을 이 컴포넌트가 소유한다 —
 * 컨테이너 ref와 `onScroll`이 여기 있으므로 훅도 여기 있어야 하고, 부모는 스크롤에 대해
 * 아무것도 모른다.
 *
 * `memo`라서 `messages` 참조가 바뀔 때(새 메시지)만 렌더되고, 그때도 아래 `ChatMessageItem`이
 * 각각 `memo`라 새 항목 하나만 마운트된다. 상한 절단으로 배열 앞이 잘려도 `key`가 `msg.id`
 * (클라이언트가 붙인 단조 증가 값)라 남은 항목은 그대로 재사용된다 — `index`를 key로 쓰면
 * 절단 한 번에 전부가 "다른 항목"이 되어 메모이제이션이 무의미해진다.
 *
 * 새 메시지뿐 아니라 **메시지 영역이 줄어들 때도** 바닥을 유지한다 — 정답이 공개되면
 * 라운드 정보 영역이 커져 영역이 줄어드는데, 그때 방금 도착한 메시지가 밀려나면
 * 공개 구간에 채팅을 살린 의미가 정확히 그 순간에 사라진다(훅 안의 `ResizeObserver`).
 */
const ChatMessageList = memo(function ChatMessageList({ messages, bottomInset = false }: ChatMessageListProps) {
    const { containerRef, endRef, onScroll } = useStickToBottom(messages);

    return (
        <div
            ref={containerRef}
            className={
                // 하단 패딩은 떠 있는 포기 컨트롤의 높이에 **딱 맞춘다**(버튼 36px + 여백 8px).
                // 넉넉히 잡으면 왼쪽이 통째로 빈 띠가 생겨 버튼이 허공에 뜬 것처럼 보인다.
                "px-4 pt-4 size-full flex flex-col gap-0.5 overflow-auto" + (bottomInset ? " pb-11" : "")
            }
            onScroll={onScroll}
        >
            {messages.map((msg) => (
                <ChatMessageItem key={msg.id} msg={msg} />
            ))}
            <div ref={endRef} />
        </div>
    );
});

export default ChatMessageList;
