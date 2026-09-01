import AppShell from "~/components/layout/AppShell";
import SearchBar from "~/components/ui/SearchBar";
import React, {useCallback, useEffect, useMemo, useRef, useState} from "react";
import { useNavigate } from "react-router";
import ColumnsContainer from "~/components/layout/ColumnsContainer";
import type RoomResponse from "~/utils/RoomResponse";
import { fetchRooms } from "~/utils/api";
import RoomCreate from "~/components/ui/RoomCreate";
import PasswordModal from "~/components/ui/PasswordModal";
import { RoomCardSkeleton } from "~/components/ui/Skeleton";
import ScrollReveal from "~/components/ui/ScrollReveal";
import { connectToRoom } from "~/utils/stomp";
import useRoomConnectionStore from "~/stores/RoomConnectionStore";
import { toast } from "sonner";

export default function RoomsView() {
    const navigate = useNavigate();
    const setConnection = useRoomConnectionStore((s) => s.setConnection);

    const [query, setQuery] = useState("");
    const [rooms, setRooms] = useState<Array<RoomResponse>>([]);
    const [showCreate, setShowCreate] = useState(false);
    const [isLoading, setIsLoading] = useState(true);
    const [connectingRoomId, setConnectingRoomId] = useState<number | null>(null);
    const [error, setError] = useState<string | null>(null);

    const [passwordModal, setPasswordModal] = useState<{ isOpen: boolean; roomId: number | null }>({ isOpen: false, roomId: null });
    const [passwordLoading, setPasswordLoading] = useState(false);
    const [passwordError, setPasswordError] = useState<string | null>(null);

    /**
     * 비밀번호 모달의 열림/닫힘마다 증가하는 세대. 요청 시점의 세대와 어긋나면
     * 그 사이 모달이 닫힌 것이므로 늦게 도착한 연결을 반영하지 않는다.
     */
    const passwordGenerationRef = useRef(0);

    const loadRooms = useCallback(() => {
        return fetchRooms()
            .then((data) => {
                setRooms(data);
                setError(null);
            })
            .catch(() => {
                setError("방 목록을 불러오지 못했습니다");
            })
            .finally(() => {
                setIsLoading(false);
            });
    }, []);

    useEffect(() => {
        loadRooms();
    }, [loadRooms]);

    const filteredRooms = useMemo(() => {
        const q = query.trim().toLowerCase();
        if (!q) return rooms;
        return rooms.filter(
            (room) =>
                room.title.toLowerCase().includes(q) ||
                room.playlist.title.toLowerCase().includes(q) ||
                room.masterDisplayName.toLowerCase().includes(q),
        );
    }, [rooms, query]);

    async function handleRoomClick(room: RoomResponse) {
        if (connectingRoomId !== null) return;

        if (room.hasPassword) {
            setPasswordModal({ isOpen: true, roomId: room.id });
            setPasswordError(null);
            return;
        }

        setConnectingRoomId(room.id);
        try {
            const client = await connectToRoom(room.id);
            setConnection(client, room.id);
            navigate(`/rooms/${room.id}`);
        } catch (error) {
            toast.error(String(error));
        } finally {
            setConnectingRoomId(null);
        }
    }

    async function handlePasswordSubmit(password: string) {
        const roomId = passwordModal.roomId;
        if (!roomId) return;

        const generation = passwordGenerationRef.current;
        setPasswordLoading(true);
        setPasswordError(null);
        try {
            const client = await connectToRoom(roomId, password);
            if (generation !== passwordGenerationRef.current) {
                // 연결이 도는 사이 모달이 닫혔다(플랫폼 강제 닫힘 포함) — 사용자가 빠져나온
                // 방으로 튕겨 넣지 않고 연결만 정리한다.
                client.deactivate();
                return;
            }
            setConnection(client, roomId);
            setPasswordModal({ isOpen: false, roomId: null });
            navigate(`/rooms/${roomId}`);
        } catch (error) {
            if (generation !== passwordGenerationRef.current) return;
            setPasswordError(String(error));
        } finally {
            setPasswordLoading(false);
        }
    }

    function handlePasswordClose() {
        passwordGenerationRef.current += 1;
        setPasswordModal({ isOpen: false, roomId: null });
        setPasswordError(null);
    }

    return (
        <AppShell variant="main" activeTab="rooms" title="플레이 룸">
            <ColumnsContainer>
                <div className="pt-4 px-4 gap-4 w-full flex flex-col items-center">
                    <div className="w-full max-w-2xl">
                        <SearchBar query={query} setQuery={setQuery} />
                    </div>
                    <div className="w-full">
                        {isLoading ? (
                            <div className="grid grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3">
                                {Array.from({ length: 8 }).map((_, i) => (
                                    <RoomCardSkeleton key={i} />
                                ))}
                            </div>
                        ) : error ? (
                            <div className="flex flex-col items-center justify-center py-20 text-zinc-500">
                                <svg xmlns="http://www.w3.org/2000/svg" className="size-12 mb-3 text-zinc-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                                    <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 1 1-18 0 9 9 0 0 1 18 0Zm-9 3.75h.008v.008H12v-.008Z" />
                                </svg>
                                <p className="text-sm font-semibold">{error}</p>
                            </div>
                        ) : filteredRooms.length === 0 && query.trim().length > 0 ? (
                            <div className="flex flex-col items-center justify-center py-20 text-zinc-500">
                                <svg xmlns="http://www.w3.org/2000/svg" className="size-12 mb-3 text-zinc-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                                    <path strokeLinecap="round" strokeLinejoin="round" d="m21 21-5.197-5.197m0 0A7.5 7.5 0 1 0 5.196 5.196a7.5 7.5 0 0 0 10.607 10.607Z" />
                                </svg>
                                <p className="text-sm font-semibold">검색 결과가 없습니다</p>
                            </div>
                        ) : (
                            <div className="grid grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3">
                                {/* 방 생성 카드 */}
                                <ScrollReveal>
                                    <button
                                        type="button"
                                        className="w-full border-2 border-dashed border-border rounded-xl flex flex-col items-center justify-center gap-2 cursor-pointer hover:border-neon-cyan/40 hover:bg-neon-cyan/[0.03] transition-all duration-300 min-h-[160px] focus:outline-none focus-visible:border-neon-cyan focus-visible:ring-2 focus-visible:ring-neon-cyan"
                                        onClick={() => setShowCreate(true)}
                                    >
                                        <div className="size-10 rounded-xl bg-neon-cyan/10 flex items-center justify-center">
                                            <span className="text-xl font-bold text-neon-cyan">+</span>
                                        </div>
                                        <span className="text-sm text-zinc-400">방 만들기</span>
                                    </button>
                                </ScrollReveal>
                                {/* 방 카드 목록 */}
                                {filteredRooms.map((room, index) => (
                                    <ScrollReveal key={room.id} delay={(index + 1) * 50}>
                                        <button
                                            type="button"
                                            onClick={() => handleRoomClick(room)}
                                            disabled={connectingRoomId === room.id}
                                            className={`block w-full text-left bg-gradient-dark border border-border rounded-xl overflow-hidden transition-all duration-300 focus:outline-none focus-visible:border-neon-cyan focus-visible:ring-2 focus-visible:ring-neon-cyan ${
                                                connectingRoomId === room.id
                                                    ? "opacity-70 pointer-events-none"
                                                    : "cursor-pointer hover:border-neon-cyan/40 hover:shadow-glow-cyan hover:-translate-y-0.5"
                                            }`}
                                        >
                                        {/* 썸네일 */}
                                        <div className="relative h-[100px] overflow-hidden">
                                            <img
                                                src={`https://img.youtube.com/vi/${room.representativeTrackEmbedId}/mqdefault.jpg`}
                                                className="w-full h-full object-cover"
                                                alt="thumbnail"
                                            />
                                            <div className="absolute inset-x-0 bottom-0 h-10 bg-gradient-to-t from-[#18181b] to-transparent" />
                                            {/* 로딩 오버레이 */}
                                            {connectingRoomId === room.id && (
                                                <div className="absolute inset-0 flex items-center justify-center bg-black/40">
                                                    <div className="size-6 border-2 border-neon-cyan border-t-transparent rounded-full animate-spin" />
                                                </div>
                                            )}
                                            {/* 뱃지 오버레이 */}
                                            <div className="absolute top-2 right-2 flex gap-1.5">
                                                {room.hasPassword && (
                                                    <div className="bg-black/60 backdrop-blur-md px-2 py-0.5 rounded-md text-[0.65rem] text-zinc-400 flex items-center">
                                                        <svg xmlns="http://www.w3.org/2000/svg" className="size-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                                            <path strokeLinecap="round" strokeLinejoin="round" d="M16.5 10.5V6.75a4.5 4.5 0 1 0-9 0v3.75m-.75 11.25h10.5a2.25 2.25 0 0 0 2.25-2.25v-6.75a2.25 2.25 0 0 0-2.25-2.25H6.75a2.25 2.25 0 0 0-2.25 2.25v6.75a2.25 2.25 0 0 0 2.25 2.25Z" />
                                                        </svg>
                                                    </div>
                                                )}
                                                <div className="bg-black/60 backdrop-blur-md px-2 py-0.5 rounded-md text-[0.65rem] text-zinc-400 flex items-center gap-1">
                                                    <svg xmlns="http://www.w3.org/2000/svg" className="size-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                                        <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 6a3.75 3.75 0 1 1-7.5 0 3.75 3.75 0 0 1 7.5 0ZM4.501 20.118a7.5 7.5 0 0 1 14.998 0A17.933 17.933 0 0 1 12 21.75c-2.676 0-5.216-.584-7.499-1.632Z" />
                                                    </svg>
                                                    {room.currentPlayerCount}/{room.maxPlayerCount}
                                                </div>
                                            </div>
                                        </div>
                                        {/* 카드 정보 */}
                                        <div className="p-3">
                                            <div className="text-sm font-semibold text-zinc-200 mb-1 truncate">{room.title}</div>
                                            <div className="text-xs text-zinc-500 truncate">{room.playlist.title} · {room.playlist.trackCount}곡</div>
                                        </div>
                                        </button>
                                    </ScrollReveal>
                                ))}
                            </div>
                        )}
                    </div>
                </div>
            </ColumnsContainer>
            <RoomCreate
                isOpen={showCreate}
                onClose={() => setShowCreate(false)}
                onRoomListStale={loadRooms}
                onCreated={async (roomId, password) => {
                    try {
                        const client = await connectToRoom(roomId, password);
                        setConnection(client, roomId);
                        navigate(`/rooms/${roomId}`);
                    } catch (error) {
                        toast.error(String(error));
                    }
                }}
            />
            <PasswordModal
                isOpen={passwordModal.isOpen}
                onClose={handlePasswordClose}
                onSubmit={handlePasswordSubmit}
                isLoading={passwordLoading}
                error={passwordError}
            />
        </AppShell>
    );
}
