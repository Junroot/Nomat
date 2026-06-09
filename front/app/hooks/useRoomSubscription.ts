import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router";
import useRoomConnectionStore from "~/stores/RoomConnectionStore";
import useMeStore from "~/stores/MeStore";
import { fetchRoomDetail } from "~/utils/api";
import type RoomDetailResponse from "~/utils/RoomDetailResponse";
import type { RoomMemberResponse, RoomStatus } from "~/utils/RoomDetailResponse";
import type RoomChatMessage from "~/utils/ChatMessage";
import type { StompSubscription } from "@stomp/stompjs";

interface RoomEventBase {
    roomId: number;
    playerId: number;
    nickname: string;
}

interface RoomJoinedLeftEvent extends RoomEventBase {
    type: "JOINED" | "LEFT";
}

interface RoomSessionReplacedEvent extends RoomEventBase {
    type: "SESSION_REPLACED";
}

interface RoomChatEvent extends RoomEventBase {
    type: "CHAT";
    content: string;
    timestamp: string;
}

interface RoomGameEvent extends RoomEventBase {
    type: "STARTED" | "ENDED";
}

type RoomEventMessage = RoomJoinedLeftEvent | RoomSessionReplacedEvent | RoomChatEvent | RoomGameEvent;

interface UseRoomSubscriptionResult {
    roomDetail: RoomDetailResponse | null;
    players: RoomMemberResponse[];
    messages: RoomChatMessage[];
    status: RoomStatus;
    isLoading: boolean;
    isDeactivated: boolean;
    sendMessage: (content: string) => void;
    startGame: () => void;
    endGame: () => void;
    leaveRoom: () => void;
}

export default function useRoomSubscription(roomId: number): UseRoomSubscriptionResult {
    const navigate = useNavigate();
    const client = useRoomConnectionStore((s) => s.client);
    const storeRoomId = useRoomConnectionStore((s) => s.roomId);
    const clear = useRoomConnectionStore((s) => s.clear);
    const meId = useMeStore((s) => s.me?.id);

    const [roomDetail, setRoomDetail] = useState<RoomDetailResponse | null>(null);
    const [players, setPlayers] = useState<RoomMemberResponse[]>([]);
    const [messages, setMessages] = useState<RoomChatMessage[]>([]);
    const [status, setStatus] = useState<RoomStatus>("ACTIVE");
    const [isLoading, setIsLoading] = useState(true);
    const [isDeactivated, setIsDeactivated] = useState(false);

    const subscriptionRef = useRef<StompSubscription | null>(null);
    const disconnectTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const isLeavingVoluntarilyRef = useRef(false);
    const isDeactivatedRef = useRef(false);

    function clearPendingTimeout() {
        if (disconnectTimeoutRef.current) {
            clearTimeout(disconnectTimeoutRef.current);
            disconnectTimeoutRef.current = null;
        }
    }

    function disconnectAndCleanup() {
        subscriptionRef.current?.unsubscribe();
        subscriptionRef.current = null;
        client?.deactivate();
        clear();
    }

    function deactivate() {
        isDeactivatedRef.current = true;
        setIsDeactivated(true);
        disconnectAndCleanup();
    }

    const handleEventRef = useRef<(event: RoomEventMessage) => void>(() => {});
    handleEventRef.current = (event: RoomEventMessage) => {
        if (event.type === "JOINED") {
            setPlayers((prev) => {
                if (prev.some((p) => p.id === event.playerId)) return prev;
                return [...prev, { id: event.playerId, nickname: event.nickname, isMaster: false }];
            });
            setMessages((prev) => [
                ...prev,
                { type: "system", eventType: "join", targetNickname: event.nickname, timestamp: new Date().toISOString() },
            ]);
        } else if (event.type === "SESSION_REPLACED") {
            if (event.playerId === meId) {
                deactivate();
                return;
            }
        } else if (event.type === "LEFT") {
            if (event.playerId === meId) {
                clearPendingTimeout();
                if (isLeavingVoluntarilyRef.current) {
                    disconnectAndCleanup();
                    navigate("/");
                } else {
                    deactivate();
                }
                return;
            }
            setPlayers((prev) => prev.filter((p) => p.id !== event.playerId));
            setMessages((prev) => [
                ...prev,
                { type: "system", eventType: "leave", targetNickname: event.nickname, timestamp: new Date().toISOString() },
            ]);
        } else if (event.type === "CHAT") {
            setMessages((prev) => [
                ...prev,
                { type: "message", senderId: event.playerId, senderNickname: event.nickname, content: event.content, timestamp: event.timestamp },
            ]);
        } else if (event.type === "STARTED") {
            setStatus("PLAYING");
            setMessages((prev) => [
                ...prev,
                { type: "system", eventType: "start", targetNickname: event.nickname, timestamp: new Date().toISOString() },
            ]);
        } else if (event.type === "ENDED") {
            setStatus("ACTIVE");
            setMessages((prev) => [
                ...prev,
                { type: "system", eventType: "end", targetNickname: event.nickname, timestamp: new Date().toISOString() },
            ]);
        }
    };

    const sendMessageRef = useRef<(content: string) => void>(() => {});
    sendMessageRef.current = (content: string) => {
        if (!client?.connected) return;
        client.publish({ destination: "/app/rooms/chat", body: JSON.stringify({ content }) });
    };

    const startGameRef = useRef<() => void>(() => {});
    startGameRef.current = () => {
        if (!client?.connected) return;
        client.publish({ destination: "/app/rooms/start" });
    };

    const endGameRef = useRef<() => void>(() => {});
    endGameRef.current = () => {
        if (!client?.connected) return;
        client.publish({ destination: "/app/rooms/end" });
    };

    const leaveRoomRef = useRef<() => void>(() => {});
    leaveRoomRef.current = () => {
        clearPendingTimeout();
        isLeavingVoluntarilyRef.current = true;
        if (client?.connected) {
            client.publish({ destination: "/app/rooms/leave" });
        }
        // 서버 LEFT 응답 미수신 시 강제 정리
        disconnectTimeoutRef.current = setTimeout(() => {
            disconnectAndCleanup();
            navigate("/");
        }, 1000);
    };

    useEffect(() => {
        clearPendingTimeout();

        if (isDeactivatedRef.current) return;

        if (!client || storeRoomId !== roomId) {
            navigate("/");
            return;
        }

        if (!subscriptionRef.current) {
            subscriptionRef.current = client.subscribe(`/topic/rooms/${roomId}`, (message) => {
                const event: RoomEventMessage = JSON.parse(message.body);
                handleEventRef.current(event);
            });

            fetchRoomDetail(roomId)
                .then((detail) => {
                    setRoomDetail(detail);
                    setPlayers(detail.players);
                    setStatus(detail.status);
                })
                .finally(() => setIsLoading(false));
        }

        return () => {
            if (isDeactivatedRef.current) return;

            // StrictMode 재마운트 시 취소되도록 지연 정리
            disconnectTimeoutRef.current = setTimeout(() => {
                if (!subscriptionRef.current) return;
                subscriptionRef.current.unsubscribe();
                subscriptionRef.current = null;
                client.publish({ destination: "/app/rooms/leave" });
                client.deactivate();
                clear();
            }, 100);
        };
    }, [client, storeRoomId, roomId, navigate, clear]);

    const sendMessage = useCallback((content: string) => sendMessageRef.current(content), []);
    const startGame = useCallback(() => startGameRef.current(), []);
    const endGame = useCallback(() => endGameRef.current(), []);
    const leaveRoom = useCallback(() => leaveRoomRef.current(), []);

    return {
        roomDetail,
        players,
        messages,
        status,
        isLoading,
        isDeactivated,
        sendMessage,
        startGame,
        endGame,
        leaveRoom,
    };
}
