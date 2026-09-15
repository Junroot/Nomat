import { create } from "zustand";
import type MeResponse from "~/utils/MeResponse";
import { fetchMe } from "~/utils/api";

// `me`를 소유한 주체가 그 적재 방법도 소유한다. 컴포넌트가 렌더 중에 요청하면
// me가 null인 매 렌더마다 재호출되고, Me는 데스크톱·모바일 양쪽에 렌더되므로
// 컴포넌트 지역 가드로는 중복을 막을 수 없다. 진행 중인 요청을 스토어에서 합친다.
interface MeState {
    me: MeResponse | null,
    ensureMe: () => Promise<void>;
}

// 렌더에 노출할 필요가 없는 내부 상태라 스토어 밖에 둔다.
let inFlight: Promise<void> | null = null

export default create<MeState>()((set, get) => ({
    me: null,
    ensureMe: () => {
        if (get().me) {
            return Promise.resolve()
        }
        if (inFlight) {
            return inFlight
        }

        // 실패는 캐시하지 않는다 — settle 시 비워야 다음 마운트에서 재시도할 수 있다.
        // 거부는 여기서 삼킨다. 401은 이미 api 인터셉터가 로그인 화면으로 보내고 있다.
        inFlight = fetchMe()
            .then((me) => set({ me }))
            .catch(() => undefined)
            .finally(() => { inFlight = null })

        return inFlight
    },
}));
