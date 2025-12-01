export default interface RoomResponse {
    id: number,
    title: string,
    playlist: RoomResponsePlaylist,
    representativeTrackEmbedId: string,
    masterDisplayName: string,
}

export interface RoomResponsePlaylist {
    id: number,
    title: string,
    trackCount: number,
}
