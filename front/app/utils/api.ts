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

client.interceptors.response.use(
    (response: AxiosResponse) => response,
    (error: AxiosError) => {
        if (error.response?.status === 403) {
            window.location.href = window.origin + "/login"
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

export async function fetchRoomDetail(roomId: number): Promise<RoomDetailResponse> {
    const response = await client.get<RoomDetailResponse>(`/rooms/${roomId}`);
    return response.data;
}

export async function fetchRooms(cursorRoomId: number = 0, size: number = 100): Promise<RoomResponse[]> {
    const response = await client.get<RoomResponse[]>("/rooms", { params: { cursorRoomId, size } });
    return response.data;
}
