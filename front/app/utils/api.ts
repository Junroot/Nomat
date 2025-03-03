import axios, { AxiosError, type AxiosResponse } from "axios";
import type MeResponse from "./MeResponse";

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

export async function fetchMe() {
    const response = await client.get<MeResponse>("/players/me")
    return response.data;
}
