import { useNavigate, useParams } from "react-router";
import { useCallback, useEffect, useRef, useState } from "react";
import AppShell from "~/components/layout/AppShell";
import PlayIcon from "~/assets/play.svg?react";
import PauseCircleIcon from "~/assets/pause-circle.svg?react";
import PlaylistIcon from "~/assets/playlist.svg?react";
import UsersIcon from "~/assets/users.svg?react";
import ColumnsContainer from "~/components/layout/ColumnsContainer";
import Column1 from "~/components/layout/Column1";
import Column2 from "~/components/layout/Column2";
import RoundPanel from "~/components/ui/RoundPanel";
import RoundAudioPlayer from "~/components/ui/RoundAudioPlayer";
import type { ClipPlaybackStatus } from "~/hooks/useClipPlayback";
import AudioGateOverlay from "~/components/ui/AudioGateOverlay";
import RoundRevealOverlay from "~/components/ui/RoundRevealOverlay";
import RoundResultOverlay from "~/components/ui/RoundResultOverlay";
import useBreakpoint from "~/hooks/useBreakpoint";
import useRoomSubscription from "~/hooks/useRoomSubscription";
import useMeStore from "~/stores/MeStore";
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

export default function RoomView() {
    const { roomId } = useParams();
    const navigate = useNavigate();
    const { isMobile } = useBreakpoint();
    const [showInfo, setShowInfo] = useState(false);

    const [input, setInput] = useState("");
    const inputRef = useRef<HTMLInputElement>(null);
    const messagesContainerRef = useRef<HTMLDivElement>(null);
    const messagesEndRef = useRef<HTMLDivElement>(null);
    const isNearBottomRef = useRef(true);

    // 오디오 arming(제스처 게이트 통과) — 방 세션 동안 유지되는 UI 상태(라운드 리듀서 밖).
    const [armed, setArmed] = useState(false);
    // 결과 오버레이 닫힘 여부 — "방으로"로 닫으면 로비 채팅으로 복귀.
    const [resultClosed, setResultClosed] = useState(false);
    // 재생 상태 — 플레이어를 방 화면이 소유하므로 이 상태도 여기서 든다(RoundPanel이 표시만 한다).
    const [playback, setPlayback] = useState<ClipPlaybackStatus>("idle");

    const { roomDetail, players, messages, status, round, isLoading, isDeactivated, sendMessage, startGame, endGame, leaveRoom } = useRoomSubscription(Number(roomId));
    const meId = useMeStore((s) => s.me?.id);

    const isMaster = players.some((p) => p.isMaster && p.id === meId);
    const isPlaying = status === "PLAYING";
    const showGate = isPlaying && !armed && !isDeactivated;
    const showReveal = round.phase === "REVEAL" && !showGate;
    const showResult = round.phase === "ENDED" && !resultClosed;

    // 자동재생이 차단되면 arming이 소실된 것으로 보고 제스처 게이트를 다시 띄운다.
    const handlePlaybackChange = useCallback((next: ClipPlaybackStatus) => {
        setPlayback(next);
        if (next === "blocked") {
            setArmed(false);
        }
    }, []);

    // 새 게임이 시작되면(phase가 ENDED를 벗어나면) 결과 오버레이 닫힘 상태를 리셋.
    useEffect(() => {
        if (round.phase !== "ENDED") setResultClosed(false);
    }, [round.phase]);
    const actions = isMaster
        ? isPlaying
            ? [{ icon: <PauseCircleIcon />, label: "게임 종료", onClick: endGame }]
            : [{ icon: <PlayIcon />, label: "시작하기", onClick: startGame }]
        : [];

    useEffect(() => {
        if (isNearBottomRef.current) {
            messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
        }
    }, [messages]);

    useEffect(() => {
        function handleKeyDown(e: KeyboardEvent) {
            if (e.key === "Enter" && document.activeElement !== inputRef.current) {
                e.preventDefault();
                inputRef.current?.focus();
            }
        }
        window.addEventListener("keydown", handleKeyDown);
        return () => window.removeEventListener("keydown", handleKeyDown);
    }, []);

    function handleSend() {
        const trimmed = input.trim();
        if (!trimmed) return;
        sendMessage(trimmed);
        setInput("");
    }

    if (isLoading || !roomDetail) {
        return (
            <AppShell variant="sub" title="로딩 중..." onBack={leaveRoom}>
                <div className="flex items-center justify-center h-full">
                    <div className="size-8 border-2 border-neon-cyan border-t-transparent rounded-full animate-spin" />
                </div>
            </AppShell>
        );
    }

    return (
        <AppShell
            variant="sub"
            title={roomDetail.title}
            onBack={leaveRoom}
            actions={actions}
        >
            <ColumnsContainer>
                {(!isMobile || showInfo) && (
                    <Column1>
                        <p className="text-4xl pt-4 font-bold hidden md:block">{roomDetail.title}</p>
                        <div className="w-full p-3 md:p-4 flex flex-col gap-1 bg-zinc-800 rounded-2xl">
                            <div className="inline-flex items-end gap-1 text-lg md:text-2xl font-bold">
                                <PlaylistIcon className="size-5 md:size-8" />
                                <p>{roomDetail.playlist.title}</p>
                            </div>
                            <p className="text-sm md:text-md text-zinc-400">by. {roomDetail.playlist.master}</p>
                            <p className="text-sm md:text-md mt-2 md:mt-4 text-zinc-200">{roomDetail.playlist.description}</p>
                        </div>
                        <div className="w-full p-3 md:p-4 flex flex-col gap-1 md:gap-2 bg-zinc-800 rounded-2xl">
                            <div className="inline-flex items-end gap-1 text-lg md:text-2xl font-bold">
                                <UsersIcon className="size-5 md:size-8" />
                                <p>플레이어</p>
                                <p className="text-sm md:text-lg">{players.length}</p>
                            </div>
                            {players.map((player) => (
                                <div key={player.id} className="flex items-center gap-2 md:gap-3 p-1.5 md:p-2 hover:bg-zinc-700/50 cursor-pointer rounded-lg transition-colors duration-200">
                                    <div className="relative">
                                        <UsersIcon className="size-7 md:size-10 rounded-full border border-zinc-600" />
                                        <div className="absolute -bottom-0.5 -right-0.5 w-2 h-2 md:w-2.5 md:h-2.5 rounded-full bg-neon-green border-2 border-zinc-800" />
                                    </div>
                                    <div className="flex items-center gap-2">
                                        <p className="text-sm md:text-base text-zinc-200">{player.nickname}</p>
                                        {player.isMaster && (
                                            <span className="bg-neon-purple/20 text-neon-purple text-xs px-1.5 py-0.5 rounded font-semibold">
                                                호스트
                                            </span>
                                        )}
                                    </div>
                                </div>
                            ))}
                        </div>
                    </Column1>
                )}
                <Column2>
                    {isMobile && (
                        <button
                            type="button"
                            className="mx-4 mt-3 px-3 py-2 flex items-center justify-between bg-zinc-800 border border-border rounded-xl text-sm text-zinc-300 cursor-pointer active:bg-zinc-700 transition-colors"
                            onClick={() => setShowInfo((v) => !v)}
                        >
                            <div className="flex items-center gap-2">
                                <UsersIcon className="size-4" />
                                <span>{showInfo ? "채팅으로 돌아가기" : "방 정보 · 플레이어"}</span>
                            </div>
                            <svg xmlns="http://www.w3.org/2000/svg" className={`size-4 text-zinc-500 transition-transform duration-200 ${showInfo ? "rotate-180" : ""}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                <path strokeLinecap="round" strokeLinejoin="round" d="m19.5 8.25-7.5 7.5-7.5-7.5" />
                            </svg>
                        </button>
                    )}
                    {/* PLAYING 중에도 채팅은 계속 노출된다 — 채팅 입력이 곧 정답 추측 채널. */}
                    {isPlaying && (
                        <RoundPanel round={round} playback={playback} />
                    )}
                    <div
                        ref={messagesContainerRef}
                        className="px-4 pt-4 w-full h-full shrink-1 flex flex-col gap-0.5 overflow-auto"
                        onScroll={() => {
                            const el = messagesContainerRef.current;
                            if (!el) return;
                            isNearBottomRef.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80;
                        }}
                    >
                        {messages.map((msg, index) => {
                            if (msg.type === "system") {
                                return (
                                    <div key={index} className="flex justify-center py-1.5">
                                        <p className="text-zinc-500 text-sm">
                                            {msg.targetNickname}님이 {SYSTEM_MESSAGE_TEXT[msg.eventType]}
                                        </p>
                                    </div>
                                );
                            }
                            return (
                                <div key={index} className="flex flex-row gap-2 px-2 py-1.5 hover:bg-zinc-800/50 rounded-lg transition-colors duration-200">
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
                        })}
                        <div ref={messagesEndRef} />
                    </div>
                    <div className="p-2 m-2 flex items-center gap-2 rounded-full bg-surface border border-border focus-within:border-neon-cyan focus-within:shadow-glow-cyan transition-all duration-200">
                        <input
                            ref={inputRef}
                            type="text"
                            placeholder="보낼 메시지 입력"
                            className="flex-1 p-[2px] pl-[8px] placeholder-zinc-500 focus:outline-none"
                            value={input}
                            onChange={(e) => setInput(e.target.value)}
                            maxLength={200}
                            onKeyDown={(e) => {
                                if (e.key === "Enter" && !e.nativeEvent.isComposing) {
                                    e.preventDefault();
                                    handleSend();
                                }
                            }}
                        />
                        <button
                            type="button"
                            className="size-7 flex items-center justify-center rounded-full bg-neon-cyan/20 text-neon-cyan hover:bg-neon-cyan/30 disabled:opacity-30 disabled:cursor-not-allowed transition-colors cursor-pointer shrink-0"
                            disabled={!input.trim()}
                            onClick={handleSend}
                        >
                            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" className="size-4">
                                <path d="M3.105 2.288a.75.75 0 0 0-.826.95l1.414 4.926A1.5 1.5 0 0 0 5.135 9.25h6.115a.75.75 0 0 1 0 1.5H5.135a1.5 1.5 0 0 0-1.442 1.086l-1.414 4.926a.75.75 0 0 0 .826.95l14.095-5.637a.75.75 0 0 0 0-1.394L3.105 2.289Z" />
                            </svg>
                        </button>
                    </div>
                </Column2>
            </ColumnsContainer>
            {/* 오디오 플레이어는 방 세션 자원이다 — `isPlaying` 밖에 두어 **게임 시작 전에** 만든다.
                그래야 아이프레임·플레이어 부트스트랩(~460ms)이 대기 중에 끝나 1라운드 재생 지연에서
                빠진다. 화면에는 정답 공개 구간에만 나타난다. */}
            <RoundAudioPlayer
                roundNumber={round.roundNumber}
                phase={round.phase}
                track={round.currentTrack}
                nextTrack={round.nextTrack}
                armed={armed}
                onPlaybackChange={handlePlaybackChange}
            />
            {showGate && <AudioGateOverlay onArm={() => setArmed(true)} />}
            {showReveal && <RoundRevealOverlay round={round} />}
            {showResult && (
                <RoundResultOverlay round={round} onClose={() => setResultClosed(true)} />
            )}
            {isDeactivated && (
                <div className="fixed inset-0 z-50 flex flex-col items-center justify-center gap-6 bg-zinc-950/80 backdrop-blur-sm">
                    <p className="text-xl font-bold text-zinc-200">다른 탭에서 사용 중입니다</p>
                    <button
                        type="button"
                        className="px-6 py-2.5 rounded-xl bg-neon-cyan/20 text-neon-cyan font-semibold hover:bg-neon-cyan/30 transition-colors cursor-pointer"
                        onClick={() => navigate("/")}
                    >
                        방 목록으로 이동
                    </button>
                </div>
            )}
        </AppShell>
    );
}
