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
}

export interface SystemMessage extends ChatMessageBase {
    type: 'system';
    eventType: 'join' | 'leave' | 'start' | 'end';
    targetNickname: string;
}

type RoomChatMessage = ChatMessage | SystemMessage;
export type { RoomChatMessage as default };
