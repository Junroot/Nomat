import axios, { AxiosError, type AxiosResponse } from "axios";
import type MeResponse from "./MeResponse";
import type PlaylistResponse from "./PlaylistResponse";
import type { PlaylistWithTracksResponse } from "./PlaylistResponse";
import type PlaylistRequest from "./PlaylistRequest";
import type PlaylistMetaDataResponse from "./PlaylistMetaDataResponse";
import type RoomResponse from "./RoomResponse";
import type RoomDetailResponse from "~/utils/RoomDetailResponse";

// 즐겨찾기 요청용 내부 타입
interface FavoritePlaylistRequest { playlistId: number }

const client = axios.create({
    baseURL: import.meta.env.VITE_SERVER_BASE_URL,
    headers: {
      "Content-Type": "application/json",
    },
    withCredentials: true,
  });

// 로그인 화면으로 보내는 유일한 주체. 다른 곳에서 /login으로 이동시키지 않는다 —
// 주체가 둘이면 어느 응답이 마지막에 도착하느냐가 최종 주소를 결정해 복귀가 간헐적으로 깨진다.
// 401(미인증)만 처리한다. 403(권한 없음)은 로그인해도 해결되지 않으므로 호출부가 처리한다.
// 첫 호출만 이동시키고 나머지는 무시해, 동시에 401이 여러 건 와도 이동이 정확히 한 번 일어난다.
// (전체 페이지 네비게이션이라 문서와 함께 플래그도 사라지므로 되돌리지 않는다.)
let redirectingToLogin = false

function redirectToLogin() {
    if (redirectingToLogin) {
        return
    }
    redirectingToLogin = true
    window.location.href = `${window.location.origin}/login?redirectUrl=${encodeURIComponent(window.location.href)}`
}

client.interceptors.response.use(
    (response: AxiosResponse) => response,
    (error: AxiosError) => {
        if (error.response?.status === 401) {
            redirectToLogin()
        }
        return Promise.reject(error)
    }
)

export async function fetchMe(): Promise<MeResponse> {
    const response = await client.get<MeResponse>("/players/me")
    return response.data;
}

export async function fetchRecentlyAddedPlaylists(): Promise<PlaylistMetaDataResponse[]> {
    const response = await client.get<PlaylistMetaDataResponse[]>("/playlists", { params: { sort: "createdAt,desc", limit: 1000 } });
    return response.data;
}

export async function fetchByMasterDisplayName(masterDisplayName: string): Promise<PlaylistMetaDataResponse[]> {
    const response = await client.get<PlaylistMetaDataResponse[]>("/playlists", { params: { masterDisplayName: masterDisplayName } });
    return response.data;
}

export async function fetchFavoritePlaylists(): Promise<PlaylistMetaDataResponse[]> {
    const response = await client.get<PlaylistMetaDataResponse[]>("/playlists", { params: { favoriteOf: "me" } });
    return response.data;
}

export async function fetchMyPlaylists(): Promise<PlaylistMetaDataResponse[]> {
    const response = await client.get<PlaylistMetaDataResponse[]>("/playlists", { params: { masterId: "me" } });
    return response.data;
}

export async function searchPlaylistsByTitle(query: string): Promise<PlaylistMetaDataResponse[]> {
    const response = await client.get<PlaylistMetaDataResponse[]>("/playlists", { params: { title: query } });
    return response.data;
}

export async function fetchPlaylist(playlistId: number): Promise<PlaylistResponse> {
    const response = await client.get<PlaylistResponse>(`/playlists/${playlistId}`);
    return response.data;
}

export async function fetchPlaylistWithTracks(playlistId: number): Promise<PlaylistWithTracksResponse> {
    const response = await client.get<PlaylistWithTracksResponse>(`/playlists/${playlistId}`, { params: { includeTracks: true } });
    return response.data;
}

export async function createPlaylist(request: PlaylistRequest): Promise<PlaylistResponse> {
    const response = await client.post<PlaylistResponse>("/playlists", request);
    return response.data;
}

export async function modifyPlaylist(playlistId: number, request: PlaylistRequest): Promise<PlaylistResponse> {
    const response = await client.put<PlaylistResponse>(`/playlists/${playlistId}`, request);
    return response.data;
}

export async function favoritePlaylist(playlistId: number): Promise<void> {
    await client.post("/favorite-playlists", { playlistId } as FavoritePlaylistRequest);
}

export async function unfavoritePlaylist(playlistId: number): Promise<void> {
    await client.delete(`/favorite-playlists/${playlistId}`);
}

export async function deletePlaylist(playlistId: number): Promise<void> {
    await client.delete(`/playlists/${playlistId}`);
}

interface RoomRequest {
    title: string;
    password?: string;
    maxEntriesCount: number;
    playlistId: number;
}

export async function createRoom(request: RoomRequest): Promise<RoomDetailResponse> {
    const response = await client.post<RoomDetailResponse>("/rooms", request);
    return response.data;
}

export async function fetchRoomDetail(roomId: number): Promise<RoomDetailResponse> {
    const response = await client.get<RoomDetailResponse>(`/rooms/${roomId}`);
    return response.data;
}

export async function fetchRooms(cursorRoomId: number = 0, size: number = 100): Promise<RoomResponse[]> {
    const response = await client.get<RoomResponse[]>("/rooms", { params: { cursorRoomId, size } });
    return response.data;
}
