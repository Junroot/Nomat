interface ChatMessageBase {
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
