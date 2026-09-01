import React, {useEffect, useState} from "react";
import {createRoom, fetchFavoritePlaylists, fetchMyPlaylists, searchPlaylistsByTitle} from "~/utils/api";
import { toast } from "sonner";
import Button from "~/components/ui/Button";
import Dropdown from "~/components/ui/Dropdown";
import Modal, {ModalTitle} from "~/components/ui/Modal";
import useInFlightGuard from "~/hooks/useInFlightGuard";

interface PlaylistOption {
    value: number;
    title: string;
}

interface RoomCreateProps {
    isOpen: boolean;
    onClose: () => void;
    onCreated: (roomId: number, password?: string) => void;
    /**
     * 모달이 닫힌 뒤 방 생성이 성공해 **자동 입장을 억제한** 경우의 신호.
     * 방은 이미 서버에 만들어져 되돌릴 수 없으므로, 부모가 방 목록을 다시 불러와
     * 사용자가 직접 입장할 수 있게 한다.
     */
    onRoomListStale: () => void;
}

const MAX_ROOM_CAPACITY = 20;

export default function RoomCreate({isOpen, onClose, onCreated, onRoomListStale}: RoomCreateProps) {
    const [roomName, setRoomName] = useState("");
    const [selectedRoomCapacity, setSelectedRoomCapacity] = useState(1);
    const [usePassword, setUsePassword] = useState(false);
    const [password, setPassword] = useState("");
    const [isCreating, setIsCreating] = useState(false);
    const [selectedPlaylist, setSelectedPlaylist] = useState<PlaylistOption | null>(null);
    const [searchTerm, setSearchTerm] = useState("");
    const [inputFocused, setInputFocused] = useState(false);
    const [filteredPlaylists, setFilteredPlaylists] = useState<PlaylistOption[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [searchError, setSearchError] = useState<string | null>(null);
    const [playlistTab, setPlaylistTab] = useState<'favorite' | 'mine' | 'all'>('favorite');
    const [favoritePlaylists, setFavoritePlaylists] = useState<PlaylistOption[]>([]);
    const [myPlaylists, setMyPlaylists] = useState<PlaylistOption[]>([]);
    const [favoriteLoaded, setFavoriteLoaded] = useState(false);
    const [myLoaded, setMyLoaded] = useState(false);
    const [favoriteLoading, setFavoriteLoading] = useState(false);
    const [myLoading, setMyLoading] = useState(false);
    const [favoriteError, setFavoriteError] = useState<string | null>(null);
    const [myError, setMyError] = useState<string | null>(null);

    // 열림/닫힘마다 무효화한다 — 모달이 닫힌 뒤 도착한 생성 성공이 자동 입장으로 이어지지 않게.
    const createGuard = useInFlightGuard();

    // 자동완성: 1글자 이상 입력 시에만 노출
    const normalizedSearch = searchTerm.trim();

    // RoomCreate는 RoomsView에 항상 마운트된 채 폼 state를 들고 있어, Modal의 children
    // 언마운트로는 리셋되지 않는다. 다시 열릴 때 직전 입력이 남지 않도록 직접 초기화한다.
    useEffect(() => {
        createGuard.invalidate();
        if (!isOpen) return;
        setRoomName("");
        setSelectedRoomCapacity(1);
        setUsePassword(false);
        setPassword("");
        setSelectedPlaylist(null);
        setSearchTerm("");
        setPlaylistTab('favorite');
    }, [isOpen, createGuard]);

    // 탭 변경 시 lazy load
    useEffect(() => {
        if (playlistTab === 'favorite' && !favoriteLoaded && !favoriteLoading) {
            setFavoriteLoading(true);
            setFavoriteError(null);
            setTimeout(() => {
                fetchFavoritePlaylists().then(data => {
                    setFavoritePlaylists(
                        data.map((p) => ({
                            value: p.id,
                            title: p.title,
                        }))
                    );
                    setFavoriteLoaded(true);
                    setFavoriteLoading(false);
                });
            }, 500);
        }
        if (playlistTab === 'mine' && !myLoaded && !myLoading) {
            setMyLoading(true);
            setMyError(null);
            setTimeout(() => {
                fetchMyPlaylists().then(data => {
                    setMyPlaylists(
                        data.map((p) => ({
                            value: p.id,
                            title: p.title,
                        }))
                    );
                    setMyLoaded(true);
                    setMyLoading(false);
                });
            }, 500);
        }
    }, [playlistTab, favoriteLoaded, myLoaded, favoriteLoading, myLoading]);

    useEffect(() => {
        if (playlistTab === 'favorite') {
            setFilteredPlaylists(
                favoritePlaylists.filter(p => p.title.toLowerCase().includes(normalizedSearch.toLowerCase()))
            );
            setIsLoading(favoriteLoading);
            setSearchError(favoriteError);
            return;
        }
        if (playlistTab === 'mine') {
            setFilteredPlaylists(
                myPlaylists.filter(p => p.title.toLowerCase().includes(normalizedSearch.toLowerCase()))
            );
            setIsLoading(myLoading);
            setSearchError(myError);
            return;
        }
        if (normalizedSearch.length < 1) {
            setFilteredPlaylists([]);
            setIsLoading(false);
            setSearchError(null);
            return;
        }
        let cancelled = false;
        setIsLoading(true);
        setSearchError(null);
        const debounceTimer = setTimeout(() => {
            searchPlaylistsByTitle(normalizedSearch)
                .then((data) => {
                    if (cancelled) return;
                    setFilteredPlaylists(
                        data.map((p) => ({
                            value: p.id,
                            title: p.title,
                        }))
                    );
                    setIsLoading(false);
                })
                .catch((e) => {
                    if (cancelled) return;
                    setFilteredPlaylists([]);
                    setIsLoading(false);
                    setSearchError("검색 실패");
                });
        }, 400);
        return () => {
            cancelled = true;
            clearTimeout(debounceTimer);
        };
    }, [favoritePlaylists, myPlaylists, normalizedSearch, playlistTab, favoriteLoading, myLoading, favoriteError, myError]);

    useEffect(() => {
        setInputFocused(false);
    }, [playlistTab]);

    // Validation
    const isValidRoomName = !!roomName && roomName.trim().length > 0;
    const isValidPassword = !usePassword || (!!password && password.length > 0);
    const isValidPlaylist = selectedPlaylist !== null;
    const isValidForm = isValidRoomName && isValidPassword && isValidPlaylist;

    useEffect(() => {
        if (!usePassword) setPassword("");
    }, [usePassword]);

    return (
        <Modal isOpen={isOpen} onClose={onClose} dismissible={!isCreating}>
            <div className="w-[28rem] max-w-full">
                <ModalTitle className="text-center">방 만들기</ModalTitle>
                <form className="flex flex-col gap-5">
                    {/* 플레이리스트 선택 */}
                    <div>
                        <label className="block mb-2 text-sm font-semibold text-zinc-300">플레이리스트</label>
                        <div className="relative">
                            {/* 탭 UI: 입력창 위에 항상 노출 */}
                            <div className="flex mb-1 gap-1">
                                <button
                                    type="button"
                                    className={`flex-1 py-1 rounded-t-lg font-semibold text-sm transition border-b-2 ${playlistTab === 'favorite' ? 'border-cyan-400 bg-zinc-800 text-cyan-300' : 'border-transparent bg-zinc-900 text-zinc-400 hover:bg-zinc-800'}`}
                                    onClick={() => {
                                        setPlaylistTab('favorite');
                                        setFilteredPlaylists([]);
                                        setSearchError(null);
                                    }}
                                >즐겨찾기
                                </button>
                                <button
                                    type="button"
                                    className={`flex-1 py-1 rounded-t-lg font-semibold text-sm transition border-b-2 ${playlistTab === 'mine' ? 'border-cyan-400 bg-zinc-800 text-cyan-300' : 'border-transparent bg-zinc-900 text-zinc-400 hover:bg-zinc-800'}`}
                                    onClick={() => {
                                        setPlaylistTab('mine');
                                        setFilteredPlaylists([]);
                                        setSearchError(null);
                                    }}
                                >내가 만든
                                </button>
                                <button
                                    type="button"
                                    className={`flex-1 py-1 rounded-t-lg font-semibold text-sm transition border-b-2 ${playlistTab === 'all' ? 'border-cyan-400 bg-zinc-800 text-cyan-300' : 'border-transparent bg-zinc-900 text-zinc-400 hover:bg-zinc-800'}`}
                                    onClick={() => {
                                        setPlaylistTab('all');
                                        setFilteredPlaylists([]);
                                        setSearchError(null);
                                    }}
                                >전체
                                </button>
                            </div>
                            {!selectedPlaylist && (
                                <div className="w-full h-10 p-2 bg-surface border border-border text-zinc-200 rounded-full focus-within:border-neon-cyan focus-within:shadow-glow-cyan transition-all duration-200">
                                    <input
                                        type="text"
                                        className="w-full pl-[8px] placeholder-zinc-500 focus:outline-none"
                                        placeholder="플레이리스트 검색"
                                        value={searchTerm}
                                        onChange={e => {
                                            setSearchTerm(e.target.value);
                                            setSelectedPlaylist(null);
                                        }}
                                        onFocus={() => setInputFocused(true)}
                                        onBlur={() => setTimeout(() => setInputFocused(false), 150)}
                                    />
                                </div>
                            )}
                            {/* 자동완성 드롭다운: 탭 없이 목록만 노출 */}
                            {inputFocused && !selectedPlaylist && (
                                <div
                                    className="absolute z-10 w-full mt-2 bg-card rounded-2xl border border-border shadow-glow-cyan max-h-56 overflow-y-auto">
                                    {isLoading && (
                                        <div className="px-4 py-2 text-zinc-400 text-center">검색 중...</div>
                                    )}
                                    {searchError && (
                                        <div className="px-4 py-2 text-red-400 text-center">{searchError}</div>
                                    )}
                                    {!isLoading && !searchError && filteredPlaylists.length === 0 && normalizedSearch.length > 0 && (
                                        <div className="px-4 py-2 text-zinc-400 text-center">검색 결과 없음</div>
                                    )}
                                    {!isLoading && !searchError && filteredPlaylists.map(p => (
                                        <div
                                            key={p.value}
                                            className="px-4 py-2 text-zinc-200 cursor-pointer rounded-2xl transition-all duration-200 hover:bg-neon-cyan/10 hover:text-neon-cyan"
                                            onMouseDown={() => {
                                                setSelectedPlaylist(p);
                                                setSearchTerm("");
                                                setInputFocused(false);
                                            }}
                                        >
                                            <span>{p.title}</span>
                                        </div>
                                    ))}
                                </div>
                            )}
                            {/* 선택된 플레이리스트 */}
                            {selectedPlaylist && (
                                <div className="flex items-center gap-2 mt-2 justify-between">
                                    <span className="text-zinc-100 font-semibold">{selectedPlaylist.title}</span>
                                    <button type="button"
                                            className="text-xs text-zinc-400 px-2 py-1 bg-zinc-800 rounded-lg border border-zinc-700 hover:bg-zinc-700 transition cursor-pointer ml-2"
                                            onClick={() => setSelectedPlaylist(null)}>
                                        선택 해제
                                    </button>
                                </div>
                            )}
                        </div>
                        {!isValidPlaylist && (
                            <div className="text-red-400 text-xs mt-1">플레이리스트를 선택하세요.</div>
                        )}
                    </div>
                    {/* 방 이름 */}
                    <div>
                        <label className="block mb-2 text-sm font-semibold text-zinc-300">방 이름</label>
                        <div className="w-full h-10 p-2 bg-surface border border-border text-zinc-200 rounded-full focus-within:border-neon-cyan focus-within:shadow-glow-cyan transition-all duration-200">
                            <input
                                type="text"
                                className="w-full pl-[8px] placeholder-zinc-500 focus:outline-none"
                                placeholder="1자 이상 입력"
                                value={roomName}
                                onChange={e => setRoomName(e.target.value)}
                            />
                        </div>
                        {!isValidRoomName && (
                            <div className="text-red-400 text-xs mt-1">방 이름을 입력하세요.</div>
                        )}
                    </div>
                    {/* 최대 인원수 */}
                    <div>
                        <label className="block mb-2 text-sm font-semibold text-zinc-300">최대 인원수</label>
                        <Dropdown
                            values={Array.from({length: MAX_ROOM_CAPACITY}, (_, i) => String(i + 1))}
                            selectedValue={String(selectedRoomCapacity)}
                            setValue={(value) => setSelectedRoomCapacity(Number(value))}
                        />
                    </div>
                    {/* 비밀번호 */}
                    <div>
                        <label className="block mb-2 text-sm font-semibold text-zinc-300">비밀번호</label>
                        <div className="flex items-center gap-2">
                            <input
                                type="checkbox"
                                checked={usePassword}
                                onChange={e => setUsePassword(e.target.checked)}
                                className="accent-secondary w-4 h-4"
                            />
                            <div className={`w-full h-10 p-2 bg-surface border border-border text-zinc-200 rounded-full focus-within:border-neon-cyan focus-within:shadow-glow-cyan transition-all duration-200 ${!usePassword ? 'opacity-50' : ''}`}>
                                <input
                                    type="password"
                                    className="w-full pl-[8px] placeholder-zinc-500 focus:outline-none"
                                    placeholder="비밀번호 입력"
                                    value={password}
                                    onChange={e => setPassword(e.target.value)}
                                    disabled={!usePassword}
                                />
                            </div>
                        </div>
                        {!isValidPassword && (
                            <div className="text-red-400 text-xs mt-1">비밀번호를 입력하세요.</div>
                        )}
                    </div>
                    {/* 버튼 */}
                    <div className="flex gap-2 mt-2">
                        <Button
                            type="button"
                            variant="primary"
                            size="lg"
                            fullWidth
                            disabled={!isValidForm || isCreating}
                            onClick={async () => {
                                if (!isValidForm || isCreating) return;
                                const token = createGuard.begin();
                                setIsCreating(true);
                                try {
                                    const room = await createRoom({
                                        title: roomName.trim(),
                                        password: usePassword ? password : undefined,
                                        maxEntriesCount: selectedRoomCapacity,
                                        playlistId: selectedPlaylist!.value,
                                    });
                                    if (!createGuard.isCurrent(token)) {
                                        // 생성이 도는 사이 모달이 닫혔다 — 사용자가 취소한 방으로
                                        // 끌고 가지 않는다. 방은 서버에 남으므로 목록만 갱신한다.
                                        onRoomListStale();
                                        return;
                                    }
                                    onClose();
                                    onCreated(room.id, usePassword ? password : undefined);
                                } catch {
                                    toast.error("방 생성에 실패했습니다.");
                                } finally {
                                    setIsCreating(false);
                                }
                            }}
                        >
                            {isCreating ? "생성 중..." : "만들기"}
                        </Button>
                        <Button
                            type="button"
                            variant="secondary"
                            size="lg"
                            fullWidth
                            onClick={onClose}
                        >
                            취소
                        </Button>
                    </div>
                </form>
            </div>
        </Modal>
    );
}
