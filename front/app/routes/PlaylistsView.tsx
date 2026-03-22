import {Link} from "react-router";
import AppShell from "~/components/layout/AppShell";
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
import { toast } from "sonner";
import Button from "~/components/ui/Button";

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
    const [favoriteKey, setFavoriteKey] = useState(0);

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
        setFavoriteKey(prev => prev + 1);
        try {
            if (optimistic.favorite) {
                await favoritePlaylist(playlistId);
                toast.info("즐겨찾기에 추가되었습니다.");
            } else {
                await unfavoritePlaylist(playlistId);
                toast.info("즐겨찾기가 해제되었습니다.");
            }
            // 즐겨찾기 탭에서 해제한 경우 목록 새로고침
            if (selectedTab === "favorite") {
                fetchFavoritePlaylists().then(p => setPlaylists(p));
            }
        } catch (e) {
            setSelectedPlaylist({...selectedPlaylist});
            toast.error("즐겨찾기 변경에 실패했습니다.");
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
            toast.success("플레이리스트가 삭제되었습니다.");
        } catch (e) {
            toast.error("삭제 중 문제가 발생했습니다.");
        } finally {
            setDeleting(false);
            setShowDeleteConfirm(false);
        }
    }

    return (
        <AppShell variant="main" activeTab="playlists" title="플레이리스트">
            <ColumnsContainer>
                <Column1>
                    <div className="bg-card rounded-lg p-1 flex gap-1 mx-4 mt-4">
                        {tabLabels.map((label, idx) => (
                            <button
                                key={tabTypes[idx]}
                                className={`flex-1 px-3 py-1.5 rounded-md text-xs font-semibold transition-all duration-200 cursor-pointer ${
                                    selectedTab === tabTypes[idx]
                                        ? "bg-neon-cyan/15 text-neon-cyan"
                                        : "text-zinc-500 hover:text-zinc-300"
                                }`}
                                onClick={() => setSelectedTab(tabTypes[idx])}
                            >
                                {label}
                            </button>
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
                                    }}
                                />
                            </div>
                            <div className="w-full h-10 p-2 shrink bg-surface border border-border text-zinc-200 rounded-full focus-within:border-neon-cyan focus-within:shadow-glow-cyan transition-all duration-200">
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
                    <div className="flex flex-col grow py-2 overflow-y-auto">
                        {playlists.length === 0 ? (
                            <div className="flex-1 flex flex-col items-center justify-center gap-4 text-zinc-500 py-8">
                                <p>아직 플레이리스트가 없습니다</p>
                                <Link to="/playlists/create">
                                    <Button variant="primary">새 플레이리스트 만들기</Button>
                                </Link>
                            </div>
                        ) : (
                            playlists.map((playlist) =>
                                <div
                                    key={playlist.id}
                                    id={playlist.id.toString()}
                                    className={`flex flex-row p-2.5 px-3 gap-3 rounded-lg transition-all duration-200 cursor-pointer hover:bg-neon-cyan/5 ${
                                        selectedPlaylistId === playlist.id
                                            ? "bg-neon-cyan/8 border border-neon-cyan/15"
                                            : "border border-transparent"
                                    }`}
                                    onClick={clickPlaylist}
                                >
                                    <img
                                        src={`https://img.youtube.com/vi/${playlist.representativeTrack.embedId}/mqdefault.jpg`}
                                        className="size-10 rounded-lg object-cover"
                                        alt="thumbnail"
                                    />
                                    <div className="flex flex-col justify-center min-w-0">
                                        <p className="text-sm font-semibold text-zinc-200 truncate">{playlist.title}</p>
                                        <p className="text-xs text-zinc-500">{playlist.master.displayName}</p>
                                    </div>
                                </div>
                            )
                        )}
                    </div>
                    <Link className="w-full pb-4" to="/playlists/create">
                        <Button variant="primary" size="lg" fullWidth>새 플레이리스트</Button>
                    </Link>
                </Column1>
                <Column2>
                    {selectedPlaylist !== null && (
                        <div className="w-full flex flex-col p-4 gap-4">
                            {/* 히어로 영역 */}
                            <div className="flex flex-col md:flex-row gap-4">
                                <img
                                    src={`https://img.youtube.com/vi/${selectedPlaylist.representativeTrack.embedId}/hq720.jpg`}
                                    className="w-full md:w-[120px] md:h-[120px] aspect-square object-cover rounded-xl border border-border shadow-[0_4px_20px_rgba(0,0,0,0.3)] shrink-0"
                                    alt="thumbnail"
                                />
                                <div className="flex flex-col gap-2 min-w-0">
                                    <p className="text-xl font-extrabold text-zinc-200">{selectedPlaylist.title}</p>
                                    <div className="flex flex-col text-xs text-zinc-500 leading-relaxed">
                                        <p><UserIcon className="inline-block size-4 mr-1"/>{selectedPlaylist.master.displayName}</p>
                                        <p><SongIcon className="inline-block size-4 mr-1"/>{selectedPlaylist.trackCount}곡 (약 {Math.round(selectedPlaylist.expectedPlayTimeSec / 60)}분)</p>
                                    </div>
                                    <p className="text-sm text-zinc-400 line-clamp-3">{selectedPlaylist.description}</p>
                                    <div className="flex flex-row items-center gap-2 mt-1">
                                        <Button
                                            variant="icon"
                                            disabled={favoriteUpdating}
                                            onClick={toggleFavorite}
                                            title={selectedPlaylist.favorite ? "즐겨찾기 해제" : "즐겨찾기 추가"}
                                        >
                                            <span key={favoriteKey} className="animate-pulse-star">
                                                {selectedPlaylist.favorite
                                                    ? <FilledStarIcon className="w-6 h-6"/>
                                                    : <StarIcon className="w-6 h-6"/>}
                                            </span>
                                        </Button>
                                        {me && me.id === selectedPlaylist.master.id && (
                                            <>
                                                <Button
                                                    variant="icon"
                                                    onClick={() => {
                                                        window.location.href = `/playlists/${selectedPlaylist.id}/modify`;
                                                    }}
                                                    title="플레이리스트 수정"
                                                >
                                                    <PencilIcon className="w-5 h-5"/>
                                                </Button>
                                                <Button
                                                    variant="icon"
                                                    onClick={() => setShowDeleteConfirm(true)}
                                                    title="플레이리스트 삭제"
                                                >
                                                    <DeleteIcon className="w-6 h-6"/>
                                                </Button>
                                            </>
                                        )}
                                    </div>
                                </div>
                            </div>
                            {/* 뮤직 플레이어 */}
                            <MusicPlayer
                                embedId={selectedPlaylist.representativeTrack.embedId}
                                startTimeSec={selectedPlaylist.representativeTrack.startTimeSec}
                                endTimeSec={selectedPlaylist.representativeTrack.endTimeSec}
                            />
                        </div>
                    )}
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
                            <Button variant="secondary" fullWidth onClick={() => setShowDeleteConfirm(false)}>
                                취소
                            </Button>
                            <Button
                                variant="danger"
                                fullWidth
                                onClick={handleConfirmDelete}
                                loading={deleting}
                            >
                                삭제
                            </Button>
                        </div>
                    </div>
                </div>
            )}
        </AppShell>
    );
}
