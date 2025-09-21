import { Link } from "react-router";
import NavigationBar from "~/components/layout/NavigationBar";
import NavigationItem from "~/components/layout/NavigationItem";
import Me from "~/components/ui/Me";
import RoomIcon from "~/assets/room.svg?react";
import PlaylistIcon from "~/assets/playlist.svg?react"
import ColumnsContainer from "~/components/layout/ColumnsContainer";
import Column1 from "~/components/layout/Column1";
import SelectMenu from "~/components/ui/SelectMenu";
import Column2 from "~/components/layout/Column2";
import React, { useEffect, useState } from "react";
import type PlaylistResponse from "~/utils/PlaylistResponse";
import {
    fetchByMasterDisplayName,
    fetchMyPlaylists,
    fetchPlaylist,
    fetchRecentlyAddedPlaylists,
    searchPlaylistsByTitle
} from "~/utils/api";
import UserIcon from "~/assets/user.svg?react";
import SongIcon from "~/assets/song.svg?react";
import MusicPlayer from "~/components/ui/MusicPlayer";
import type PlaylistMetaDataResponse from "~/utils/PlaylistMetaDataResponse";
import { getRegistrationCode } from "~/utils/registrationCode";

export default function PlaylistsView() {
    const searchTypes = ["제목", "제작자"];
    const [selectedSearchType, setSelectedSearchType] = useState(searchTypes[0]);
    const [query, setQuery] = useState("");
    const [debouncedQuery, setDebouncedQuery] = useState("");
    const [selectedPlaylistId, setSelectedPlaylistId] = useState<number | null>(null);
    const [selectedPlaylist, setSelectedPlaylist] = useState<PlaylistResponse | null>(null);
    const tabTypes = ["favorite", "mine", "all"];
    const tabLabels = ["즐겨찾기", "내가 만든", "전체"];
    const [selectedTab, setSelectedTab] = useState<string>(tabTypes[0]);
    const [playlists, setPlaylists] = useState<PlaylistMetaDataResponse[]>([]);

    useEffect(() => {
        if (selectedPlaylistId === null) {
            return;
        }

        fetchPlaylist(selectedPlaylistId)
            .then(playlist => setSelectedPlaylist(playlist));
    }, [selectedPlaylistId])

    useEffect(() => {
        if (selectedTab === "all") {
            if (debouncedQuery.length > 0) {
                if (selectedSearchType === "제작자") {
                    fetchByMasterDisplayName(debouncedQuery)
                        .then(playlists => setPlaylists(playlists));
                    return;
                } else if (selectedSearchType === "제목") {
                    searchPlaylistsByTitle(debouncedQuery)
                        .then(playlists => setPlaylists(playlists));
                }
            } else {
                fetchRecentlyAddedPlaylists()
                    .then(playlists => setPlaylists(playlists));
            }
        } else if (selectedTab === "mine") {
            fetchMyPlaylists()
                .then(playlists => setPlaylists(playlists));
        }
    }, [selectedTab, selectedSearchType, debouncedQuery]);

    // 검색어 디바운스
    useEffect(() => {
        const handler = setTimeout(() => {
            setDebouncedQuery(query.trim());
        }, 400); // 400ms 디바운스
        return () => clearTimeout(handler);
    }, [query]);

    function clickPlaylist(event: React.MouseEvent<HTMLDivElement>) {
        const id = event.currentTarget.id;
        setSelectedPlaylistId(parseInt(id));
    }

    return (
        <div className="flex flex-row w-full h-full">
            <NavigationBar >
                <div className="grow shrink flex flex-col gap-4">
                    <Link to="/" replace>
                        <NavigationItem icon={<RoomIcon></RoomIcon>} title={"플레이 룸"}></NavigationItem>
                    </Link>
                    <NavigationItem clicked icon={<PlaylistIcon></PlaylistIcon>} title={"플레이리스트"}></NavigationItem>
                </div>
                <div className="grow-0 shrink-0"><Me></Me></div>
            </NavigationBar>
            <ColumnsContainer>
                <Column1>
                    <div className="flex flex-row p-4 gap-4">
                        {tabLabels.map((label, idx) => (
                            <p
                                key={tabTypes[idx]}
                                className={`text-3xl font-bold cursor-pointer ${selectedTab === tabTypes[idx] ? "text-zinc-200" : "text-zinc-600"}`}
                                onClick={() => setSelectedTab(tabTypes[idx])}
                            >
                                {label}
                            </p>
                        ))}
                    </div>
                    {selectedTab === "all" && (
                        <div className="flex flex-row gap-2">
                            <div className="w-32">
                                <SelectMenu
                                    options={searchTypes}
                                    selectedOption={selectedSearchType}
                                    selectedHandler={(selectedOption: string) => {
                                            setSelectedSearchType(selectedOption);
                                        }
                                    }
                                >
                                </SelectMenu>
                            </div>
                            <div className="w-full h-10 p-2 shrink bg-zinc-600 text-zinc-200 rounded-full">
                                <input
                                    type="text"
                                    placeholder="검색어"
                                    value={query}
                                    onChange={(e) => setQuery(e.target.value)}
                                    className="w-full pl-[8px] focus:outline-none"
                                />
                            </div>
                        </div>
                    )}
                    <div className="flex flex-col grow py-2 bg-zinc-800 rounded-2xl overflow-y-auto">
                        {
                            playlists.map((playlist, i) =>
                                <div key={i} id={playlist.id} className="flex flex-row p-2 gap-4 hover:bg-zinc-600" onClick={clickPlaylist}>
                                    <img
                                        src={`https://img.youtube.com/vi/${playlist.representativeTrack.embedId}/mqdefault.jpg`}
                                        className="size-16 object-cover"
                                        alt="tumbnail"
                                    >
                                    </img>
                                    <div className="flex flex-col justify-center">
                                        <p className="text-xl font-bold">{playlist.title}</p>
                                        <p className="text-zinc-400">{playlist.master.displayName}</p>
                                    </div>
                                </div>
                            )
                        }
                    </div>
                        <Link className="w-full" to="/playlists/create">
                            <button className="w-full bg-cyan-400 py-2 text-zinc-900 rounded-full cursor-pointer text-2xl font-bold">
                                새 플레이리스트
                            </button>
                        </Link>
                </Column1>
                <Column2>
                    {
                        selectedPlaylist !== null &&
                            <div className="w-full h-full flex flex-col justify-center p-4">
                                <div className="w-full flex flex-col bg-zinc-800 text-zinc-200 rounded-2xl p-8 gap-4">
                                    <p className="text-4xl font-bold">{selectedPlaylist.title}</p>
                                    <div className="flex flex-col">
                                        <p><UserIcon className="inline-block"/>{selectedPlaylist.master.displayName}</p>
                                        <p><SongIcon className="inline-block"/>{selectedPlaylist.trackCount}곡 (약 {Math.round(selectedPlaylist.expectedPlayTimeSec / 60)}분)</p>
                                    </div>
                                    <div className="flex flex-col">
                                        <p className="text-3xl font-bold">소개</p>
                                        <p>{selectedPlaylist.description}</p>
                                    </div>
                                    <div className="flex flex-col">
                                        <p className="text-3xl font-bold">대표곡 미리 듣기</p>
                                        <MusicPlayer embedId={selectedPlaylist.representativeTrack.embedId} startTimeSec={selectedPlaylist.representativeTrack.startTimeSec} endTimeSec={selectedPlaylist.representativeTrack.endTimeSec}></MusicPlayer>
                                    </div>
                                    <div className="flex flex-col items-center">
                                        <img
                                            src={`https://img.youtube.com/vi/${selectedPlaylist.representativeTrack.embedId}/hq720.jpg`}
                                            className="size-64 object-cover"
                                            alt="tumbnail"
                                        >
                                        </img>
                                    </div>
                                </div>
                            </div>
                    }
                </Column2>
            </ColumnsContainer>
        </div>
      );
}
