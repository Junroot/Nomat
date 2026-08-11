/**
 * 우리가 실제로 호출하는 YouTube 플레이어 메서드만 추린 구조적 타입.
 *
 * ⚠️ `react-youtube`가 export하는 `YouTubePlayer`를 쓰지 않는 이유: 그 타입은
 * `youtube-player/dist/types`에서 재export되는데 해당 패키지는 **선언 파일을 배포하지 않는다**
 * (`.js`와 `.js.flow`만 있다). tsconfig의 `skipLibCheck: true`가 그 해석 실패를 삼켜
 * `YouTubePlayer`가 조용히 `any`로 떨어진다 — 타입을 붙인 것처럼 보이지만 오타 하나 못 잡는다.
 * (`--skipLibCheck false`로 돌리면 `TS7016: Could not find a declaration file for module
 * 'youtube-player/dist/types'`로 드러난다.)
 *
 * 그래서 쓰는 표면만 직접 선언한다. 부수 효과로 이 인터페이스가 곧 "우리가 의존하는 플레이어 API
 * 목록"이 된다 — 새 메서드를 쓰려면 여기에 먼저 추가해야 한다.
 */
export interface YouTubePlayerHandle {
    playVideo(): void;
    pauseVideo(): void;
    stopVideo(): void;
    seekTo(seconds: number, allowSeekAhead: boolean): void;
    loadVideoById(clip: ClipRef): void;
    cueVideoById(clip: ClipRef): void;
    mute(): void;
    unMute(): void;
    setVolume(volume: number): void;
    /**
     * 아래 둘은 youtube-player 래퍼가 **프록시하지 않는다.** 이벤트가 가공 없이 재발행돼
     * `event.target`이 원본 `YT.Player`인 덕에 호출할 수 있을 뿐이라, 존재를 보장할 수 없어
     * 옵셔널로 둔다. 호출부는 `?.`와 try/catch로 방어한다.
     */
    unloadModule?(module: string): void;
    addEventListener?(event: string, listener: () => void): void;
}

/** `loadVideoById`/`cueVideoById`가 받는 클립 경계. */
export interface ClipRef {
    videoId: string;
    startSeconds: number;
    endSeconds: number;
}
