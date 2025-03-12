import axios, { AxiosError, type AxiosResponse } from "axios";
import type MeResponse from "./MeResponse";
import type PlaylistResponse from "./PlaylistResponse";

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

export async function fetchPlaylist(playlistId: string): Promise<PlaylistResponse> {
    return {
        title: "오늘의 TOP 100: 일본",
        creatorNickname: "ROOT#DSCD",
        songCount: 100,
        expectedTimeSec: 6000,
        description: "오늘의 일본 인기곡 Top 100으로 구성된 맵입니다. 재미있게 즐겨 주세요!",
        representSong: {
            youtubeKey: "lWl5viCqGSc",
            startTimeSec: 60,
            endTimeSec: 120,
        }
    }
}
