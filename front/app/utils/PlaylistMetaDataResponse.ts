export default interface PlaylistMetaDataResponse {
    id: number,
    title: string,
    representativeTrack: PlaylistMetaDataResponseTrack,
    master: PlaylistMetaDataResponseMaster,
    description: string,
}

export interface PlaylistMetaDataResponseTrack {
    embedId: string,
    title: string,
}

export interface PlaylistMetaDataResponseMaster {
    id: number,
    nickname: string,
    registrationType: string,
    displayName: string,
}
