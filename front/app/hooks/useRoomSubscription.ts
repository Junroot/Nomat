import { useEffect, useCallback, useState } from "react";
import { useNavigate } from "react-router";
import useRoomConnectionStore from "~/stores/RoomConnectionStore";
import { fetchRoomDetail } from "~/utils/api";
import type RoomDetailResponse from "~/utils/RoomDetailResponse";
import type { RoomMemberResponse } from "~/utils/RoomDetailResponse";
import type { SystemMessage } from "~/utils/ChatMessage";

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
}

export default function useRoomSubscription(roomId: number): UseRoomSubscriptionResult {
    const navigate = useNavigate();
    const client = useRoomConnectionStore((s) => s.client);
    const storeRoomId = useRoomConnectionStore((s) => s.roomId);
    const clear = useRoomConnectionStore((s) => s.clear);

    const [roomDetail, setRoomDetail] = useState<RoomDetailResponse | null>(null);
    const [players, setPlayers] = useState<RoomMemberResponse[]>([]);
    const [systemMessages, setSystemMessages] = useState<SystemMessage[]>([]);
    const [isLoading, setIsLoading] = useState(true);

    const handleEvent = useCallback((event: RoomEventMessage) => {
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
            setPlayers((prev) => prev.filter((p) => p.id !== event.playerId));
            setSystemMessages((prev) => [
                ...prev,
                { type: "system", eventType: "leave", targetNickname: event.nickname, timestamp: new Date().toISOString() },
            ]);
        }
    }, []);

    useEffect(() => {
        if (!client || storeRoomId !== roomId) {
            navigate("/");
            return;
        }

        const subscription = client.subscribe(`/topic/rooms/${roomId}`, (message) => {
            const event: RoomEventMessage = JSON.parse(message.body);
            handleEvent(event);
        });

        fetchRoomDetail(roomId)
            .then((detail) => {
                setRoomDetail(detail);
                setPlayers(detail.players);
            })
            .finally(() => setIsLoading(false));

        return () => {
            subscription.unsubscribe();
            client.deactivate();
            clear();
        };
    }, [client, storeRoomId, roomId, navigate, handleEvent, clear]);

    return { roomDetail, players, systemMessages, isLoading };
}
