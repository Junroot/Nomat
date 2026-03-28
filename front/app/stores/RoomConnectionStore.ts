import { create } from "zustand";
import type { Client } from "@stomp/stompjs";

interface RoomConnectionState {
    client: Client | null;
    roomId: number | null;
    setConnection: (client: Client, roomId: number) => void;
    clear: () => void;
}

export default create<RoomConnectionState>()((set) => ({
    client: null,
    roomId: null,
    setConnection: (client, roomId) => set({ client, roomId }),
    clear: () => set({ client: null, roomId: null }),
}));
