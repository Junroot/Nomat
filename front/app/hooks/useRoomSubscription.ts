import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router";
import useRoomConnectionStore from "~/stores/RoomConnectionStore";
import useMeStore from "~/stores/MeStore";
import { fetchRoomDetail } from "~/utils/api";
import type RoomDetailResponse from "~/utils/RoomDetailResponse";
import type { RoomMemberResponse } from "~/utils/RoomDetailResponse";
import type { SystemMessage } from "~/utils/ChatMessage";
import type { StompSubscription } from "@stomp/stompjs";

interface RoomEventMessage {
    type: "JOINED" | "LEFT";
    roomId: number;
    playerId: number;
    nickname: string;
}

interface UseRoomSubscriptionResult {
    roomDetail: RoomDetailResponse | null;
    players: RoomMemberResponse[];
    systemMessages: SystemMessage[];
    isLoading: boolean;
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
    const [systemMessages, setSystemMessages] = useState<SystemMessage[]>([]);
    const [isLoading, setIsLoading] = useState(true);

    const subscriptionRef = useRef<StompSubscription | null>(null);
    const disconnectTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

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

    const handleEventRef = useRef<(event: RoomEventMessage) => void>(() => {});
    handleEventRef.current = (event: RoomEventMessage) => {
        if (event.type === "JOINED") {
            setPlayers((prev) => {
                if (prev.some((p) => p.id === event.playerId)) return prev;
                return [...prev, { id: event.playerId, nickname: event.nickname, isMaster: false }];
            });
            setSystemMessages((prev) => [
                ...prev,
                { type: "system", eventType: "join", targetNickname: event.nickname, timestamp: new Date().toISOString() },
            ]);
        } else if (event.type === "LEFT") {
            if (event.playerId === meId) {
                clearPendingTimeout();
                disconnectAndCleanup();
                navigate("/");
                return;
            }
            setPlayers((prev) => prev.filter((p) => p.id !== event.playerId));
            setSystemMessages((prev) => [
                ...prev,
                { type: "system", eventType: "leave", targetNickname: event.nickname, timestamp: new Date().toISOString() },
            ]);
        }
    };

    const leaveRoomRef = useRef<() => void>(() => {});
    leaveRoomRef.current = () => {
        clearPendingTimeout();
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
                })
                .finally(() => setIsLoading(false));
        }

        return () => {
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

    return {
        roomDetail,
        players,
        systemMessages,
        isLoading,
        leaveRoom: () => leaveRoomRef.current(),
    };
}
