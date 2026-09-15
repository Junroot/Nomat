import { useCallback, useEffect, useReducer, useRef, useState } from "react";
import { useNavigate } from "react-router";
import { toast } from "sonner";
import type { AxiosError } from "axios";
import useRoomConnectionStore from "~/stores/RoomConnectionStore";
import useMeStore from "~/stores/MeStore";
import { fetchRoomDetail } from "~/utils/api";
import type RoomDetailResponse from "~/utils/RoomDetailResponse";
import type { RoomMemberResponse, RoomStatus } from "~/utils/RoomDetailResponse";
import type RoomChatMessage from "~/utils/ChatMessage";
import ColorSlotAllocator from "~/utils/ColorSlotAllocator";
import type { RoundStartedEvent, RoundRevealedEvent, RoundPassUpdatedEvent } from "~/utils/RoundEvent";
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
    | RoundRevealedEvent
    | RoundPassUpdatedEvent;

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
    /** 포기("모르겠어요") 토글. 같은 호출이 켜고 끄기를 겸한다. */
    pass: (roundSeq: number) => void;
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
     * 방 세션 동안의 playerId → 닉네임 색 슬롯 대응. 훅 인스턴스(= 방 세션)와 수명이 같고
     * 언마운트되면 함께 버려진다.
     *
     * 상태가 아니라 ref인 이유: 대응 자체는 렌더에 쓰이지 않는다(메시지에 슬롯을 찍는 시점에만
     * 읽음). 상태로 두면 배정할 때마다 불필요한 렌더가 나고, 이펙트 재실행(StrictMode)에도
     * ref는 유지된다.
     */
    const colorSlotsRef = useRef(new ColorSlotAllocator());

    /**
     * 피드에 메시지를 붙이는 **유일한** 경로 — id 부여와 상한 절단을 여기서만 한다.
     *
     * 메시지 객체는 여기서 만들어진 뒤 **절대 변경하지 않는다.** 목록 항목(`ChatMessageItem`)이
     * `React.memo`로 props 동일성만 보고 렌더를 건너뛰므로, 객체를 제자리에서 고치면 화면이
     * 갱신되지 않는다. 바꿔야 할 일이 생기면 새 객체로 교체한다. `colorSlot`도 그 대상이다 —
     * 이후 배정 상태가 바뀌어도(입퇴장) 이미 피드에 있는 메시지의 슬롯은 손대지 않는다.
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
            // 중복 JOINED(이미 목록에 있음)여도 assign은 기존 슬롯을 돌려주므로 색이 유지된다.
            colorSlotsRef.current.assign(event.playerId);
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
            // 떠난 사람의 슬롯은 비워 다음 입장자가 재사용한다. 남은 사람의 슬롯은 건드리지 않는다.
            // 본인 LEFT(위에서 return)와 SESSION_REPLACED는 배정에 영향 없음.
            colorSlotsRef.current.release(event.playerId);
            setPlayers((prev) => prev.filter((p) => p.id !== event.playerId));
            appendMessage({ type: "system", eventType: "leave", targetNickname: event.nickname, timestamp: new Date().toISOString() });
        } else if (event.type === "CHAT") {
            // STOMP 구독이 방 상세보다 먼저 열리므로 멤버 목록을 받기 전에 CHAT이 올 수 있다.
            // 여기서도 배정해 그 발신자에게 색이 비지 않게 하고, 이후 방 상세 배정은 이 슬롯을 유지한다.
            const colorSlot = colorSlotsRef.current.assign(event.playerId);
            appendMessage({ type: "message", senderId: event.playerId, senderNickname: event.nickname, content: event.content, timestamp: event.timestamp, colorSlot });
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
        } else if (event.type === "ROUND_PASS_UPDATED") {
            dispatchRound({ type: "PASS_UPDATED", event });
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

    const passRef = useRef<(roundSeq: number) => void>(() => {});
    passRef.current = (roundSeq: number) => {
        if (!client?.connected) return;
        // 본인 표시는 브로드캐스트에 실리지 않으므로(누가 눌렀는지 비공개) 여기서 낙관적으로 뒤집는다.
        // 서버가 무시한 신호는 다음 ROUND_STARTED가 초기화하고, 인원수는 서버 이벤트가 정정한다.
        dispatchRound({ type: "PASS_TOGGLED" });
        client.publish({ destination: "/app/rooms/pass", body: JSON.stringify({ roundSeq }) });
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
                    // 목록 순서대로 슬롯 배정. 방 상세보다 먼저 온 CHAT으로 이미 배정된 참가자는
                    // assign이 기존 슬롯을 돌려주므로 별도 분기가 필요 없다.
                    detail.players.forEach((p) => colorSlotsRef.current.assign(p.id));
                    setPlayers(detail.players);
                    setStatus(detail.status);
                    // 재접속 복원: 진행 중 라운드 스냅샷이 있으면 리듀서를 시드한다(roundSeq 단조 가드).
                    if (detail.round) {
                        dispatchRound({ type: "HYDRATE", snapshot: detail.round });
                    }
                })
                .catch((error) => {
                    // 방 멤버가 아닌 상태의 방 조회는 403이다. 미처리 rejection을 남기지 않는다.
                    const axiosError = error as AxiosError<{ message: string }>;
                    toast.error(axiosError.response?.data?.message ?? "방 정보를 불러오지 못했습니다.");
                    navigate("/");
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
    const pass = useCallback((roundSeq: number) => passRef.current(roundSeq), []);
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
        pass,
        leaveRoom,
    };
}
