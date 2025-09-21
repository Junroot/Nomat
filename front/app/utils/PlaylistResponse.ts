export default interface PlaylistResponse {
    id: number,
    title: string,
    description: string,
    master: PlaylistResponseMaster
    trackCount: number,
    expectedPlayTimeSec: number,
    representativeTrack: PlaylistResponseTrack,
}

export interface PlaylistResponseMaster {
    id: number,
    nickname: string,
    displayName: string,
}

export interface PlaylistResponseTrack {
    embedId: string,
    startTimeSec: number,
    endTimeSec: number,
}
