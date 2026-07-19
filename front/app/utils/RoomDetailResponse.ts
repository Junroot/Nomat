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
    // 재접속 복원용 라운드 스냅샷. PLAYING 중이 아니면 서버가 생략한다(하위호환).
    round?: RoundSnapshotResponse;
}
