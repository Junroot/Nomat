import { create } from "zustand";
import type MeResponse from "~/utils/MeResponse";

interface MeState {
    me: MeResponse | null,
    setMe: (newMe: MeResponse) => void;
}

export default create<MeState>()((set) => ({
    me: null,
    setMe: (newMe) => set({ me: newMe }),
}));
