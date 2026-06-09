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
}
