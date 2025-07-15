export default interface PlaylistResponse {
    id: number,
    title: string,
    description: string,
    masterNickname: string,
    tracks: PlaylistResponseTrack[],
}

export interface PlaylistResponseTrack {
    embedId: string,
    title: string,
    startTimeSec: number,
    endTimeSec: number,
    repeatCount: number,
    addtionalTitles: string[],
    isRepresentative: boolean,
}