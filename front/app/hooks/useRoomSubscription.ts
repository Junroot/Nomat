import { useCallback, useEffect, useReducer, useRef, useState } from "react";
import { useNavigate } from "react-router";
import useRoomConnectionStore from "~/stores/RoomConnectionStore";
import useMeStore from "~/stores/MeStore";
import { fetchRoomDetail } from "~/utils/api";
import type RoomDetailResponse from "~/utils/RoomDetailResponse";
import type { RoomMemberResponse, RoomStatus } from "~/utils/RoomDetailResponse";
import type RoomChatMessage from "~/utils/ChatMessage";
import type { RoundStartedEvent, RoundRevealedEvent } from "~/utils/RoundEvent";
import { roundReducer, initialRoundState, type RoundState } from "~/hooks/roundReducer";
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

interface RoomGameEvent extends Omit<RoomEventBase, "playerId" | "nickname"> {
    type: "STARTED" | "ENDED";
    // 방장 수동 종료는 행위자를 싣고, 서버 주도(자연) 종료는 null이다.
    playerId: number | null;
    nickname: string | null;
}

type RoomEventMessage =
    | RoomJoinedLeftEvent
    | RoomSessionReplacedEvent
    | RoomChatEvent
    | RoomGameEvent
    | RoundStartedEvent
    | RoundRevealedEvent;

/**
 * 채팅 피드가 유지하는 최근 메시지 수. 상한을 넘으면 가장 오래된 것부터 버린다.
 *
 * 왜 300인가 — 화면에는 15~20개가 보인다. 한 라운드에 추측이 20개씩 나와도 15라운드 전까지
 * 되짚을 수 있어 "아까 누가 뭐라 했지"를 충분히 감당한다. 항목 하나가 DOM 노드 약 12개
 * (inline SVG 포함)이므로 3,600노드 안팎 — 가상화 없이 무난한 크기다.
 *
 * 값을 바꾸면 `openspec/specs/room-round-ui/spec.md`의 "현재 N = 300"도 같은 커밋에서 갱신한다.
 */
const MAX_CHAT_MESSAGES = 300;

// 유니온의 각 멤버에 개별 적용되는 Omit — 그냥 Omit은 유니온을 공통 키로 뭉개 분기(type)가 사라진다.
type DistributiveOmit<T, K extends keyof T> = T extends unknown ? Omit<T, K> : never;

/** 피드에 들어가기 전, id가 아직 없는 메시지. id는 `appendMessage`가 붙인다. */
type ChatMessageInput = DistributiveOmit<RoomChatMessage, "id">;

interface UseRoomSubscriptionResult {
    roomDetail: RoomDetailResponse | null;
    players: RoomMemberResponse[];
    messages: RoomChatMessage[];
    status: RoomStatus;
    round: RoundState;
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
    const [round, dispatchRound] = useReducer(roundReducer, initialRoundState);
    const [isLoading, setIsLoading] = useState(true);
    const [isDeactivated, setIsDeactivated] = useState(false);

    const subscriptionRef = useRef<StompSubscription | null>(null);
    const disconnectTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const isLeavingVoluntarilyRef = useRef(false);
    const isDeactivatedRef = useRef(false);
    const nextMessageIdRef = useRef(0);

    /**
     * 피드에 메시지를 붙이는 **유일한** 경로 — id 부여와 상한 절단을 여기서만 한다.
     *
     * 메시지 객체는 여기서 만들어진 뒤 **절대 변경하지 않는다.** 목록 항목(`ChatMessageItem`)이
     * `React.memo`로 props 동일성만 보고 렌더를 건너뛰므로, 객체를 제자리에서 고치면 화면이
     * 갱신되지 않는다. 바꿔야 할 일이 생기면 새 객체로 교체한다.
     *
     * 절단은 한 번에 한 항목씩 일어난다(상한 도달 후 메시지 하나마다 하나 제거). 위로 스크롤해
     * 과거를 읽는 사람의 화면은 브라우저 scroll anchoring에 맡기며, 미지원 브라우저(Safari)에서도
     * 이동이 항목 하나 높이를 넘지 않는 것은 이 "하나씩" 성질 덕분이다.
     */
    function appendMessage(msg: ChatMessageInput) {
        const withId = { ...msg, id: nextMessageIdRef.current++ } as RoomChatMessage;
        setMessages((prev) => {
            const kept = prev.length >= MAX_CHAT_MESSAGES ? prev.slice(prev.length - MAX_CHAT_MESSAGES + 1) : prev;
            return [...kept, withId];
        });
    }

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
            appendMessage({ type: "system", eventType: "join", targetNickname: event.nickname, timestamp: new Date().toISOString() });
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
            appendMessage({ type: "system", eventType: "leave", targetNickname: event.nickname, timestamp: new Date().toISOString() });
        } else if (event.type === "CHAT") {
            appendMessage({ type: "message", senderId: event.playerId, senderNickname: event.nickname, content: event.content, timestamp: event.timestamp });
        } else if (event.type === "STARTED") {
            setStatus("PLAYING");
            dispatchRound({ type: "GAME_STARTED" });
            const nickname = event.nickname;
            if (nickname) {
                appendMessage({ type: "system", eventType: "start", targetNickname: nickname, timestamp: new Date().toISOString() });
            }
        } else if (event.type === "ENDED") {
            setStatus("ACTIVE");
            dispatchRound({ type: "GAME_ENDED" });
            // 서버 주도(자연) 종료는 행위자가 없다(nickname=null) — 방장 수동 종료일 때만 시스템 메시지.
            const nickname = event.nickname;
            if (nickname) {
                appendMessage({ type: "system", eventType: "end", targetNickname: nickname, timestamp: new Date().toISOString() });
            }
        } else if (event.type === "ROUND_STARTED") {
            dispatchRound({ type: "ROUND_STARTED", event });
        } else if (event.type === "ROUND_REVEALED") {
            dispatchRound({ type: "ROUND_REVEALED", event });
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
                    // 재접속 복원: 진행 중 라운드 스냅샷이 있으면 리듀서를 시드한다(roundSeq 단조 가드).
                    if (detail.round) {
                        dispatchRound({ type: "HYDRATE", snapshot: detail.round });
                    }
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
        round,
        isLoading,
        isDeactivated,
        sendMessage,
        startGame,
        endGame,
        leaveRoom,
    };
}
