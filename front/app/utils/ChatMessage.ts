interface ChatMessageBase {
    // 클라이언트가 부여하는 단조 증가 식별자. 서버 이벤트에는 id가 없고 timestamp는 같은
    // 밀리초에 겹칠 수 있어 목록 key로 쓸 수 없다. 피드 상한으로 앞이 잘려도 이 값은 그대로다.
    id: number;
    timestamp: string;
}

export interface ChatMessage extends ChatMessageBase {
    type: 'message';
    senderId: number;
    senderNickname: string;
    content: string;
    // 발신자의 닉네임 색 슬롯(0 ~ CHAT_COLOR_SLOT_COUNT-1). 발신 시점에 확정되며 이후 바뀌지
    // 않는다 — 메시지 객체는 불변이고 `ChatMessageItem`의 memo가 그 위에 서 있다. 입퇴장으로
    // 배정 상태가 바뀌어도 이미 피드에 있는 메시지의 색은 그대로다.
    colorSlot: number;
}

export interface SystemMessage extends ChatMessageBase {
    type: 'system';
    eventType: 'join' | 'leave' | 'start' | 'end';
    targetNickname: string;
}

type RoomChatMessage = ChatMessage | SystemMessage;
export type { RoomChatMessage as default };
