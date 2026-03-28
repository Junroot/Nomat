import { Client } from "@stomp/stompjs";

const CONNECT_TIMEOUT_MS = 5000;

function getWsUrl(): string {
    const baseUrl = import.meta.env.VITE_SERVER_BASE_URL as string;
    return baseUrl.replace(/^http/, "ws") + "/ws";
}

export function connectToRoom(roomId: number, password?: string): Promise<Client> {
    return new Promise((resolve, reject) => {
        const client = new Client({
            brokerURL: getWsUrl(),
            connectHeaders: {
                roomId: String(roomId),
                ...(password != null && { password }),
            },
            reconnectDelay: 0,
        });

        const timeout = setTimeout(() => {
            client.deactivate();
            reject("연결 시간이 초과되었습니다.");
        }, CONNECT_TIMEOUT_MS);

        client.onConnect = () => {
            clearTimeout(timeout);
            resolve(client);
        };

        client.onStompError = (frame) => {
            clearTimeout(timeout);
            const message = frame.headers["message"] || "방 입장에 실패했습니다.";
            client.deactivate();
            reject(message);
        };

        client.onWebSocketError = () => {
            clearTimeout(timeout);
            client.deactivate();
            reject("서버에 연결할 수 없습니다.");
        };

        client.activate();
    });
}
