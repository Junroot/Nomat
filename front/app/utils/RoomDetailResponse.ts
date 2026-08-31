import type { RoundSnapshotResponse } from "./RoundEvent";

export interface RoomMemberResponse {
    id: number;
    nickname: string;
    isMaster: boolean;
}

export interface PlaylistDetailResponse {
    id: number;
    title: string;
    count: number;
    master: string;
    description: string;
}

export type RoomStatus = "PENDING" | "ACTIVE" | "PLAYING";

export default interface RoomDetailResponse {
    id: number;
    title: string;
    playlist: PlaylistDetailResponse;
    players: RoomMemberResponse[];
    status: RoomStatus;
    // 재접속 복원용 라운드 스냅샷. PLAYING 중이 아니면 키가 빠지는 것이 아니라 `null`로 내려온다
    // (백엔드 `RoomDetailResponse.round`는 nullable이고 Jackson inclusion이 기본값 ALWAYS다).
    round: RoundSnapshotResponse | null;
}
