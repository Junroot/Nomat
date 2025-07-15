export default interface PlaylistRequest {
    title: string;
    description: string;
    tracks: PlaylistRequestTrack[];
}

export interface PlaylistRequestTrack {
    embedId: string;
    title: string;
    startTimeSec: number;
    endTimeSec: number;
    repeatCount: number;
    addtionalTitles: string[];
    isRepresentative: boolean;
}
