import {Link} from "react-router";
import NavigationBar from "~/components/layout/NavigationBar";
import NavigationItem from "~/components/layout/NavigationItem";
import Me from "~/components/ui/Me";
import RoomIcon from "~/assets/room.svg?react";
import PlaylistIcon from "~/assets/playlist.svg?react"
import ColumnsContainer from "~/components/layout/ColumnsContainer";
import Column1 from "~/components/layout/Column1";
import SelectMenu from "~/components/ui/SelectMenu";
import Column2 from "~/components/layout/Column2";
import React, {useEffect, useState} from "react";
import type PlaylistResponse from "~/utils/PlaylistResponse";
import {
    deletePlaylist,
    favoritePlaylist,
    fetchByMasterDisplayName,
    fetchFavoritePlaylists,
    fetchMyPlaylists,
    fetchPlaylist,
    fetchRecentlyAddedPlaylists,
    searchPlaylistsByTitle,
    unfavoritePlaylist
} from "~/utils/api";
import UserIcon from "~/assets/user.svg?react";
import SongIcon from "~/assets/song.svg?react";
import MusicPlayer from "~/components/ui/MusicPlayer";
import type PlaylistMetaDataResponse from "~/utils/PlaylistMetaDataResponse";
import StarIcon from "~/assets/star.svg?react";
import FilledStarIcon from "~/assets/filled-star.svg?react";
import PencilIcon from "~/assets/pencil.svg?react";
import DeleteIcon from "~/assets/delete.svg?react";
import useMeStore from "~/stores/MeStore";

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
    const [favoriteUpdating, setFavoriteUpdating] = useState(false);
    const me = useMeStore(state => state.me);
    const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
    const [deleting, setDeleting] = useState(false);

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
        } else if (selectedTab === "favorite") {
            fetchFavoritePlaylists()
                .then(playlists => setPlaylists(playlists));
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

    async function toggleFavorite() {
        if (!selectedPlaylist || favoriteUpdating) return;
        setFavoriteUpdating(true);
        const playlistId = selectedPlaylist.id;
        const optimistic = {...selectedPlaylist, favorite: !selectedPlaylist.favorite} as PlaylistResponse;
        setSelectedPlaylist(optimistic);
        try {
            if (optimistic.favorite) {
                await favoritePlaylist(playlistId);
            } else {
                await unfavoritePlaylist(playlistId);
            }
            // 즐겨찾기 탭에서 해제한 경우 목록 새로고침
            if (selectedTab === "favorite") {
                fetchFavoritePlaylists().then(p => setPlaylists(p));
            }
        } catch (e) {
            setSelectedPlaylist({...selectedPlaylist});
        } finally {
            setFavoriteUpdating(false);
        }
    }

    async function handleConfirmDelete() {
        if (!selectedPlaylist) return;
        setDeleting(true);
        try {
            await deletePlaylist(selectedPlaylist.id);
            setPlaylists(prev => prev.filter(p => p.id !== selectedPlaylist.id));
            setSelectedPlaylist(null);
            setSelectedPlaylistId(null);
        } catch (e) {
            // 단순 경고 (필요시 향후 토스트로 대체 가능)
            alert("삭제 중 문제가 발생했습니다.");
        } finally {
            setDeleting(false);
            setShowDeleteConfirm(false);
        }
    }

    return (
        <div className="flex flex-row w-full h-full">
            <NavigationBar>
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
                    <div className="flex flex-row px-4 pt-4 gap-4">
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
                            <div className="w-36">
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
                                <div key={i} id={playlist.id.toString()}
                                     className="flex flex-row p-2 gap-4 hover:bg-zinc-600" onClick={clickPlaylist}>
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
                        <button
                            className="w-full bg-cyan-400 py-2 text-zinc-900 rounded-full cursor-pointer text-2xl font-bold">
                            새 플레이리스트
                        </button>
                    </Link>
                </Column1>
                <Column2>
                    {
                        selectedPlaylist !== null &&
                        <div className="w-full h-full flex flex-col justify-center p-4">
                            <div className="w-full flex flex-col bg-zinc-800 text-zinc-200 rounded-2xl p-8 gap-4">
                                <div className="flex flex-row justify-between items-center">
                                    <p className="text-4xl font-bold">{selectedPlaylist.title}</p>
                                    <div className="flex flex-row items-center gap-2">
                                        {me && me.id === selectedPlaylist.master.id && (
                                            <button
                                                className="size-10 flex items-center justify-center rounded-full bg-zinc-700 hover:bg-zinc-600 text-sm cursor-pointer"
                                                onClick={() => setShowDeleteConfirm(true)}
                                                title="플레이리스트 삭제"
                                            >
                                                <DeleteIcon className="w-6 h-6"/>
                                            </button>
                                        )}
                                        {me && me.id === selectedPlaylist.master.id && (
                                            <button
                                                className="size-10 flex items-center justify-center rounded-full bg-zinc-700 hover:bg-zinc-600 text-sm cursor-pointer"
                                                onClick={() => {
                                                    window.location.href = `/playlists/${selectedPlaylist.id}/modify`;
                                                }}
                                                title="플레이리스트 수정"
                                            >
                                                <PencilIcon className="w-5 h-5"/>
                                            </button>
                                        )}
                                        <button
                                            disabled={favoriteUpdating}
                                            onClick={toggleFavorite}
                                            className={`size-10 flex items-center justify-center rounded-full transition-colors bg-zinc-700 hover:bg-zinc-600 ${favoriteUpdating ? "opacity-50 cursor-not-allowed" : "cursor-pointer"}`}
                                            title={selectedPlaylist.favorite ? "즐겨찾기 해제" : "즐겨찾기 추가"}
                                        >
                                            {selectedPlaylist.favorite ? <FilledStarIcon className="w-6 h-6"/> :
                                                <StarIcon className="w-6 h-6"/>}
                                        </button>
                                    </div>
                                </div>
                                <div className="flex flex-col">
                                    <p><UserIcon className="inline-block"/>{selectedPlaylist.master.displayName}</p>
                                    <p><SongIcon className="inline-block"/>{selectedPlaylist.trackCount}곡
                                        (약 {Math.round(selectedPlaylist.expectedPlayTimeSec / 60)}분)</p>
                                </div>
                                <div className="flex flex-col">
                                    <p className="text-3xl font-bold">소개</p>
                                    <p>{selectedPlaylist.description}</p>
                                </div>
                                <div className="flex flex-col">
                                    <p className="text-3xl font-bold">대표곡 미리 듣기</p>
                                    <MusicPlayer embedId={selectedPlaylist.representativeTrack.embedId}
                                                 startTimeSec={selectedPlaylist.representativeTrack.startTimeSec}
                                                 endTimeSec={selectedPlaylist.representativeTrack.endTimeSec}></MusicPlayer>
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
            {/* 삭제 확인 모달 */}
            {showDeleteConfirm && (
                <div className="fixed inset-0 flex items-center justify-center z-50">
                    <div className="absolute inset-0 bg-black opacity-50"></div>
                    <div className="bg-zinc-800 text-zinc-200 rounded-2xl p-8 z-10 w-96">
                        <p className="text-xl font-bold mb-4">플레이리스트 삭제</p>
                        <p className="mb-4">선택한 플레이리스트를 정말로 삭제하시겠습니까?</p>
                        <div className="flex flex-row justify-end gap-2">
                            <button
                                onClick={() => setShowDeleteConfirm(false)}
                                className="flex-1 bg-zinc-700 hover:bg-zinc-600 rounded-full py-2 text-center font-bold transition-colors cursor-pointer"
                            >
                                취소
                            </button>
                            <button
                                onClick={handleConfirmDelete}
                                className={`flex-1 bg-red-600 hover:bg-red-500 rounded-full py-2 text-center font-bold transition-colors ${deleting ? "opacity-50 cursor-not-allowed" : "cursor-pointer"}`}
                                disabled={deleting}
                            >
                                {deleting ? "삭제 중..." : "삭제"}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
