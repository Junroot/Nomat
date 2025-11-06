import React, {useEffect, useState} from "react";

interface PlaylistOption {
    value: number;
    title: string;
}

interface RoomCreateProps {
    onClose: () => void;
}

const MAX_ROOM_CAPACITY = 20;

export default function RoomCreate({onClose}: RoomCreateProps) {
    const [roomName, setRoomName] = useState("");
    const [selectedRoomCapacity, setSelectedRoomCapacity] = useState(1);
    const [usePassword, setUsePassword] = useState(false);
    const [password, setPassword] = useState("");
    const [selectedPlaylist, setSelectedPlaylist] = useState<PlaylistOption | null>(null);
    const [searchTerm, setSearchTerm] = useState("");
    const [inputFocused, setInputFocused] = useState(false);

    // 단일 mock 데이터
    const allPlaylists: PlaylistOption[] = [
        {value: 301, title: "전체1"},
        {value: 302, title: "전체2"},
        {value: 303, title: "전체3"},
        {value: 304, title: "테스트 플레이리스트"},
        {value: 305, title: "노래방"},
    ];

    // 자동완성: 1글자 이상 입력 시에만 노출
    const normalizedSearch = searchTerm.trim().toLowerCase();
    const showAutocomplete = inputFocused && !selectedPlaylist && normalizedSearch.length >= 1;
    const filteredPlaylists = normalizedSearch.length >= 1
        ? allPlaylists.filter(p => p.title.toLowerCase().includes(normalizedSearch))
        : [];

    // Validation
    const isValidRoomName = !!roomName && roomName.trim().length > 0;
    const isValidPassword = !usePassword || (!!password && password.length > 0);
    const isValidPlaylist = selectedPlaylist !== null;
    const isValidForm = isValidRoomName && isValidPassword && isValidPlaylist;

    useEffect(() => {
        if (!usePassword) setPassword("");
    }, [usePassword]);

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-60">
            <div className="bg-zinc-900 rounded-xl p-6 w-full max-w-md shadow-2xl border border-zinc-800">
                <div className="text-xl font-bold mb-6 text-center text-zinc-100">방 만들기</div>
                <form className="flex flex-col gap-5">
                    {/* 플레이리스트 선택 */}
                    <div>
                        <label className="block mb-2 text-sm font-semibold text-zinc-300">플레이리스트</label>
                        <div className="relative">
                            {!selectedPlaylist &&
                                <input
                                    type="text"
                                    className="w-full rounded-lg border border-zinc-700 bg-zinc-800 px-3 py-2 text-zinc-100 placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-secondary transition"
                                    placeholder="플레이리스트 검색"
                                    value={searchTerm}
                                    onChange={e => {
                                        setSearchTerm(e.target.value);
                                        setSelectedPlaylist(null);
                                    }}
                                    onFocus={() => setInputFocused(true)}
                                    onBlur={() => setTimeout(() => setInputFocused(false), 150)}
                                />
                            }
                            {showAutocomplete && (
                                <div
                                    className="absolute z-10 w-full bg-zinc-900 rounded-b-lg border border-zinc-700 shadow-lg py-2 max-h-56 overflow-y-auto">
                                    {filteredPlaylists.map(p => (
                                        <div
                                            key={p.value}
                                            className="px-3 py-2 text-zinc-100 cursor-pointer hover:bg-zinc-700 transition"
                                            onMouseDown={() => {
                                                setSelectedPlaylist(p);
                                                setSearchTerm("");
                                                setInputFocused(false);
                                            }}
                                        >
                                            {p.title}
                                        </div>
                                    ))}
                                </div>
                            )}
                            {selectedPlaylist && (
                                <div className="flex items-center gap-2 mt-2">
                                    <span className="text-zinc-100 font-semibold">{selectedPlaylist.title}</span>
                                    <button type="button"
                                            className="text-xs text-zinc-400 px-2 py-1 bg-zinc-800 rounded-lg border border-zinc-700 hover:bg-zinc-700 transition cursor-pointer"
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
                        <input
                            type="text"
                            className="w-full rounded-lg border border-zinc-700 bg-zinc-800 px-3 py-2 text-zinc-100 placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-secondary transition"
                            placeholder="1자 이상 입력"
                            value={roomName}
                            onChange={e => setRoomName(e.target.value)}
                        />
                        {!isValidRoomName && (
                            <div className="text-red-400 text-xs mt-1">방 이름을 입력하세요.</div>
                        )}
                    </div>
                    {/* 최대 인원수 */}
                    <div>
                        <label className="block mb-2 text-sm font-semibold text-zinc-300">최대 인원수</label>
                        <select
                            className="w-full rounded-lg border border-zinc-700 bg-zinc-800 px-3 py-2 text-zinc-100 focus:outline-none focus:ring-2 focus:ring-secondary transition"
                            value={selectedRoomCapacity}
                            onChange={e => setSelectedRoomCapacity(Number(e.target.value))}
                        >
                            {Array.from({length: MAX_ROOM_CAPACITY}, (_, i) => i + 1).map(n => (
                                <option key={n} value={n}>{n}</option>
                            ))}
                        </select>
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
                            <input
                                type="password"
                                className={`w-full rounded-lg border border-zinc-700 bg-zinc-800 px-3 py-2 text-zinc-100 placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-secondary transition ${!usePassword ? 'opacity-50' : ''}`}
                                placeholder="비밀번호 입력"
                                value={password}
                                onChange={e => setPassword(e.target.value)}
                                disabled={!usePassword}
                            />
                        </div>
                        {!isValidPassword && (
                            <div className="text-red-400 text-xs mt-1">비밀번호를 입력하세요.</div>
                        )}
                    </div>
                    {/* 버튼 */}
                    <div className="flex gap-2 mt-2">
                        <button
                            type="button"
                            className={`flex-1 rounded-lg py-2 font-bold transition ${isValidForm ? 'bg-cyan-400 text-zinc-900 hover:bg-cyan-400/80 cursor-pointer' : 'bg-zinc-700 text-zinc-400 cursor-not-allowed'}`}
                            disabled={!isValidForm}
                            onClick={() => {
                                if (!isValidForm) return;
                                // TODO: 실제 방 생성 API 호출
                                onClose();
                            }}
                        >
                            만들기
                        </button>
                        <button
                            type="button"
                            className="flex-1 rounded-lg py-2 bg-zinc-700 text-zinc-200 font-bold hover:bg-zinc-600 transition"
                            onClick={onClose}
                        >
                            취소
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}
