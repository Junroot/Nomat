export default interface PlaylistResponse {
    id: number,
    title: string,
    description: string,
    master: PlaylistResponseMaster
    trackCount: number,
    expectedPlayTimeSec: number,
    favorite: boolean,
    representativeTrack: PlaylistResponseRepresentativeTrack,
}

export interface PlaylistWithTracksResponse {
    id: number,
    title: string,
    description: string,
    tracks: PlaylistWithTracksResponseTrack[],
}

export interface PlaylistResponseMaster {
    id: number,
    nickname: string,
    displayName: string,
}

export interface PlaylistWithTracksResponseTrack {
    embedId: string,
    title: string,
    startTimeSec: number,
    endTimeSec: number,
    repeatCount: number,
    additionalTitles: string[],
    isRepresentative: boolean,
}

export interface PlaylistResponseRepresentativeTrack {
    embedId: string,
    startTimeSec: number,
    endTimeSec: number,
}
