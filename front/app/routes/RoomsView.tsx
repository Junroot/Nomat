import AppShell from "~/components/layout/AppShell";
import SearchBar from "~/components/ui/SearchBar";
import React, {useEffect, useState} from "react";
import { Link } from "react-router";
import ColumnsContainer from "~/components/layout/ColumnsContainer";
import type RoomResponse from "~/utils/RoomResponse";
import RoomCreate from "~/components/ui/RoomCreate";

export default function RoomsView() {
    const [query, setQuery] = useState("");
    const [rooms, setRooms] = useState<Array<RoomResponse>>([]);
    const [showCreate, setShowCreate] = useState(false);

    useEffect(() => {
        setRooms(
            [
                {
                    id: 1,
                    title: "테스트 방 1",
                    playlist: {
                        id: 1,
                        title: "테스트 플레이리스트 1",
                        trackCount: 10,
                    },
                    representativeTrackEmbedId: "sPLqsLsooJY",
                    masterDisplayName: "플레이어1",
                },
                {
                    id: 1,
                    title: "테스트 방 1",
                    playlist: {
                        id: 1,
                        title: "테스트 플레이리스트 1",
                        trackCount: 10,
                    },
                    representativeTrackEmbedId: "sPLqsLsooJY",
                    masterDisplayName: "플레이어1",
                },
                {
                    id: 1,
                    title: "테스트 방 1",
                    playlist: {
                        id: 1,
                        title: "테스트 플레이리스트 1",
                        trackCount: 10,
                    },
                    representativeTrackEmbedId: "sPLqsLsooJY",
                    masterDisplayName: "플레이어1",
                },
                {
                    id: 1,
                    title: "테스트 방 1",
                    playlist: {
                        id: 1,
                        title: "테스트 플레이리스트 1",
                        trackCount: 10,
                    },
                    representativeTrackEmbedId: "sPLqsLsooJY",
                    masterDisplayName: "플레이어1",
                },
            ]
        )
    }, []);

    return (
        <AppShell variant="main" activeTab="rooms" title="플레이 룸">
            <ColumnsContainer>
                <div className="pt-4 px-4 gap-4 w-full flex flex-col items-center">
                    <div className="w-full max-w-2xl">
                        <SearchBar query={query} setQuery={setQuery}></SearchBar>
                    </div>
                    <div className="w-full">
                        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4">
                            <div className="m-2 p-4 cursor-pointer bg-zinc-800 rounded-lg flex items-center justify-center hover:bg-zinc-700 transition-colors min-h-[88px] min-w-[88px]" onClick={() => setShowCreate(true)}>
                                <span className="text-4xl font-bold text-zinc-200">+</span>
                            </div>
                            {
                                rooms.map((room) => (
                                    <Link to={`/rooms/${room.id}`} key={room.id} className="m-2 p-4 bg-zinc-800 rounded-lg hover:bg-zinc-700 transition-colors">
                                        <div className="flex flex-row items-center gap-4">
                                            <div className="flex-shrink-0">
                                                <img
                                                    src={`https://img.youtube.com/vi/${room.representativeTrackEmbedId}/mqdefault.jpg`}
                                                    className="w-20 h-20 object-cover rounded-md shadow-md"
                                                    alt="thumbnail"
                                                />
                                            </div>
                                            <div className="flex flex-col justify-center flex-1">
                                                <h2 className="text-lg font-bold">{room.title}</h2>
                                                <p className="text-sm text-zinc-300">
                                                    플레이리스트: <span className="font-semibold text-zinc-200">{room.playlist.title}</span>
                                                </p>
                                                <p className="text-sm text-zinc-300">
                                                    곡 수: <span className="font-semibold text-zinc-200">{room.playlist.trackCount}곡</span>
                                                </p>
                                                <p className="text-sm text-zinc-300">
                                                    방장: <span className="font-semibold text-zinc-200">{room.masterDisplayName}</span>
                                                </p>
                                            </div>
                                        </div>
                                    </Link>
                                ))
                            }
                        </div>
                    </div>
                </div>
            </ColumnsContainer>
            {showCreate && <RoomCreate onClose={() => setShowCreate(false)} />}
        </AppShell>
      );
}
