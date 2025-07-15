import axios, { AxiosError, type AxiosResponse } from "axios";
import type MeResponse from "./MeResponse";
import type PlaylistResponse from "./PlaylistResponse";
import type PlaylistRequest from "./PlaylistRequest";

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
    }
)

export async function fetchMe(): Promise<MeResponse> {
    const response = await client.get<MeResponse>("/players/me")
    return response.data;
}

export async function fetchPlaylist(playlistId: number): Promise<PlaylistResponse> {
    return {
        id: playlistId,
        title: "오늘의 TOP 100: 일본",
        description: "오늘의 일본 인기곡 Top 100으로 구성된 맵입니다. 재미있게 즐겨 주세요!",
        masterNickname: "ROOT#DSCD",
        tracks: [
            {
                embedId: "lWl5viCqGSc",
                title: "유령도쿄",
                startTimeSec: 0,
                endTimeSec: 100,
                repeatCount: 1,
                addtionalTitles: [],
                isRepresentative: true,
            }
        ]
    }
}

export async function createPlaylist(request: PlaylistRequest): Promise<void> {
    await client.post("/playlists", request);
}
