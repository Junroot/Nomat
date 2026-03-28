import { useParams } from "react-router";
import { useState } from "react";
import AppShell from "~/components/layout/AppShell";
import PlayIcon from "~/assets/play.svg?react";
import PlaylistIcon from "~/assets/playlist.svg?react";
import UsersIcon from "~/assets/users.svg?react";
import ColumnsContainer from "~/components/layout/ColumnsContainer";
import Column1 from "~/components/layout/Column1";
import Column2 from "~/components/layout/Column2";
import useBreakpoint from "~/hooks/useBreakpoint";
import useRoomSubscription from "~/hooks/useRoomSubscription";
import type RoomChatMessage from "~/utils/ChatMessage";

const NEON_COLORS = [
    "text-neon-cyan",
    "text-neon-purple",
    "text-neon-pink",
    "text-neon-green",
] as const;

function nicknameColor(senderId: number): string {
    return NEON_COLORS[senderId % NEON_COLORS.length];
}

function formatTime(timestamp: string): string {
    const d = new Date(timestamp);
    return d.toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" });
}

export default function RoomView() {
    const { roomId } = useParams();
    const { isMobile } = useBreakpoint();
    const [showInfo, setShowInfo] = useState(false);

    const { roomDetail, players, systemMessages, isLoading, leaveRoom } = useRoomSubscription(Number(roomId));

    if (isLoading || !roomDetail) {
        return (
            <AppShell variant="sub" title="로딩 중..." onBack={leaveRoom}>
                <div className="flex items-center justify-center h-full">
                    <div className="size-8 border-2 border-neon-cyan border-t-transparent rounded-full animate-spin" />
                </div>
            </AppShell>
        );
    }

    const messages: RoomChatMessage[] = systemMessages;

    return (
        <AppShell
            variant="sub"
            title={roomDetail.title}
            onBack={leaveRoom}
            actions={[{ icon: <PlayIcon />, label: "시작하기", onClick: () => {} }]}
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
                    <div className="px-4 pt-4 w-full h-full shrink-1 flex flex-col gap-0.5 overflow-auto">
                        {messages.map((msg, index) => {
                            if (msg.type === "system") {
                                return (
                                    <div key={index} className="flex justify-center py-1.5">
                                        <p className="text-zinc-500 text-sm">
                                            {msg.targetNickname}님이 {msg.eventType === "join" ? "입장" : "퇴장"}했습니다
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
                    </div>
                    <div className="p-2 m-2 rounded-full bg-surface border border-border focus-within:border-neon-cyan focus-within:shadow-glow-cyan transition-all duration-200">
                        <input
                            type="text"
                            placeholder="보낼 메시지 입력"
                            className="w-full p-[2px] pl-[8px] placeholder-zinc-500 focus:outline-none"
                        />
                    </div>
                </Column2>
            </ColumnsContainer>
        </AppShell>
    );
}
