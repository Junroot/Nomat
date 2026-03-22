import AppShell from "~/components/layout/AppShell";
import SearchBar from "~/components/ui/SearchBar";
import React, {useEffect, useMemo, useState} from "react";
import { Link } from "react-router";
import ColumnsContainer from "~/components/layout/ColumnsContainer";
import type RoomResponse from "~/utils/RoomResponse";
import RoomCreate from "~/components/ui/RoomCreate";

export default function RoomsView() {
    const [query, setQuery] = useState("");
    const [rooms, setRooms] = useState<Array<RoomResponse>>([]);
    const [showCreate, setShowCreate] = useState(false);

    useEffect(() => {
        setRooms([
            {
                id: 1,
                title: "K-POP 퀴즈방",
                playlist: { id: 1, title: "내가 만든 플리", trackCount: 12 },
                representativeTrackEmbedId: "sPLqsLsooJY",
                masterDisplayName: "플레이어1",
                currentPlayerCount: 3,
                maxPlayerCount: 20,
                hasPassword: false,
            },
            {
                id: 2,
                title: "2000년대 히트곡",
                playlist: { id: 2, title: "올드보이", trackCount: 8 },
                representativeTrackEmbedId: "dTAAsCNK7RA",
                masterDisplayName: "올드보이",
                currentPlayerCount: 7,
                maxPlayerCount: 10,
                hasPassword: false,
            },
            {
                id: 3,
                title: "친구들만의 방",
                playlist: { id: 3, title: "뮤직러버", trackCount: 20 },
                representativeTrackEmbedId: "9bZkp7q19f0",
                masterDisplayName: "뮤직러버",
                currentPlayerCount: 2,
                maxPlayerCount: 5,
                hasPassword: true,
            },
            {
                id: 4,
                title: "애니 OST 맞추기",
                playlist: { id: 4, title: "오타쿠", trackCount: 15 },
                representativeTrackEmbedId: "UxxajLWwzqY",
                masterDisplayName: "오타쿠",
                currentPlayerCount: 5,
                maxPlayerCount: 10,
                hasPassword: false,
            },
        ]);
    }, []);

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

    return (
        <AppShell variant="main" activeTab="rooms" title="플레이 룸">
            <ColumnsContainer>
                <div className="pt-4 px-4 gap-4 w-full flex flex-col items-center">
                    <div className="w-full max-w-2xl">
                        <SearchBar query={query} setQuery={setQuery} />
                    </div>
                    <div className="w-full">
                        {filteredRooms.length === 0 && query.trim().length > 0 ? (
                            <div className="flex flex-col items-center justify-center py-20 text-zinc-500">
                                <svg xmlns="http://www.w3.org/2000/svg" className="size-12 mb-3 text-zinc-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                                    <path strokeLinecap="round" strokeLinejoin="round" d="m21 21-5.197-5.197m0 0A7.5 7.5 0 1 0 5.196 5.196a7.5 7.5 0 0 0 10.607 10.607Z" />
                                </svg>
                                <p className="text-sm font-semibold">검색 결과가 없습니다</p>
                            </div>
                        ) : filteredRooms.length === 0 && query.trim().length === 0 && rooms.length === 0 ? (
                            <div className="flex flex-col items-center justify-center py-20 text-zinc-500">
                                <svg xmlns="http://www.w3.org/2000/svg" className="size-12 mb-3 text-zinc-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                                    <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 6a3.75 3.75 0 1 1-7.5 0 3.75 3.75 0 0 1 7.5 0ZM4.501 20.118a7.5 7.5 0 0 1 14.998 0A17.933 17.933 0 0 1 12 21.75c-2.676 0-5.216-.584-7.499-1.632Z" />
                                </svg>
                                <p className="text-sm font-semibold">아직 방이 없습니다</p>
                                <p className="text-xs mt-1">새로운 방을 만들어보세요</p>
                            </div>
                        ) : (
                            <div className="grid grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3">
                                {/* 방 생성 카드 */}
                                <div
                                    className="border-2 border-dashed border-border rounded-xl flex flex-col items-center justify-center gap-2 cursor-pointer hover:border-neon-cyan/40 hover:bg-neon-cyan/[0.03] transition-all duration-300 min-h-[160px] animate-stagger-fade-in"
                                    onClick={() => setShowCreate(true)}
                                >
                                    <div className="size-10 rounded-xl bg-neon-cyan/10 flex items-center justify-center">
                                        <span className="text-xl font-bold text-neon-cyan">+</span>
                                    </div>
                                    <span className="text-sm text-zinc-400">방 만들기</span>
                                </div>
                                {/* 방 카드 목록 */}
                                {filteredRooms.map((room, index) => (
                                    <Link
                                        to={`/rooms/${room.id}`}
                                        key={room.id}
                                        className="bg-gradient-dark border border-border rounded-xl overflow-hidden hover:border-neon-cyan/40 hover:shadow-glow-cyan hover:-translate-y-0.5 transition-all duration-300 animate-stagger-fade-in"
                                        style={{ animationDelay: `${(index + 1) * 50}ms` }}
                                    >
                                        {/* 썸네일 */}
                                        <div className="relative h-[100px] overflow-hidden">
                                            <img
                                                src={`https://img.youtube.com/vi/${room.representativeTrackEmbedId}/mqdefault.jpg`}
                                                className="w-full h-full object-cover"
                                                alt="thumbnail"
                                            />
                                            <div className="absolute inset-x-0 bottom-0 h-10 bg-gradient-to-t from-[#18181b] to-transparent" />
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
                                    </Link>
                                ))}
                            </div>
                        )}
                    </div>
                </div>
            </ColumnsContainer>
            {showCreate && <RoomCreate onClose={() => setShowCreate(false)} />}
        </AppShell>
    );
}
