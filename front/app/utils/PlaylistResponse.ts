export default interface PlaylistResponse {
    title: string,
    creatorNickname: string,
    songCount: number,
    expectedTimeSec: number,
    description: string,
    representSong: RepresentSongResponse,
}

export interface RepresentSongResponse {
    youtubeKey: string,
    startTimeSec: number,
    endTimeSec: number,
}