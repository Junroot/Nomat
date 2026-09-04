import { useEffect, useRef, useState } from "react";
import PlayIcon from "~/assets/play-circle.svg?react";
import PauseIcon from "~/assets/pause-circle.svg?react";
import YouTube from "react-youtube";
import useVolumeStore from "~/stores/VolumeStore";

interface MusicPlayerProps {
    embedId: string,
    startTimeSec: number,
    endTimeSec: number,
    title?: string,
}

function formatTime(sec: number): string {
    const minutes = Math.floor(sec / 60);
    const seconds = Math.floor(sec % 60);
    return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

export default function MusicPlayer({ embedId, startTimeSec, endTimeSec, title }: MusicPlayerProps) {
    const [isPlaying, setIsPlaying] = useState(false);
    const [progress, setProgress] = useState(0);
    const [currentTimeSec, setCurrentTimeSec] = useState(0);
    const playerRef = useRef<any>(null);
    const animationFrameRef = useRef<number | null>(null);
    const totalDuration = endTimeSec - startTimeSec;

    // 볼륨은 앱 전역 설정을 따른다. `onReady`는 이벤트 콜백이라 클로저의 옛 값을 읽을 수 있으므로
    // ref로 최신 값을 보장한다(라운드 오케스트레이터와 같은 패턴).
    const volume = useVolumeStore((state) => state.volume);
    const volumeRef = useRef(volume);
    volumeRef.current = volume;

    // 유튜브 플레이어가 로드될 때 실행
    const onReady = (event: any) => {
        playerRef.current = event.target;
        playerRef.current.setVolume(volumeRef.current);
    };

    // 재생 중 볼륨이 바뀌면 즉시 반영한다.
    useEffect(() => {
        playerRef.current?.setVolume(volume);
    }, [volume]);

    const youTubeOptions = {
        playerVars: {
            cc_load_policy: 0,
            controls: 0,
            disablekb: 1,
            iv_load_policy: 3,
            modestbranding: 1,
            fs: 0,
            rel: 0,
            showinfo: 0,
            playsinline: 1,
            start: startTimeSec,
            end: endTimeSec,
        }
    }
    const togglePlay = () => {
        if (!playerRef.current) {
            return;
        }

        if (isPlaying) {
            playerRef.current.pauseVideo();
            cancelAnimationFrame(animationFrameRef.current!);
        } else {
            if (playerRef.current.getCurrentTime() >= endTimeSec) {
                playerRef.current.seekTo(startTimeSec);
            }
            playerRef.current.playVideo();
            updateProgress();
        }

        setIsPlaying(!isPlaying);
    }

    const updateProgress = () => {
        if (!playerRef.current) {
            return;
        }

        const currentTime = playerRef.current.getCurrentTime();
        const elapsed = currentTime - startTimeSec;
        setCurrentTimeSec(Math.max(0, elapsed));
        setProgress(((currentTime - startTimeSec) / (endTimeSec - startTimeSec)) * 100);

        animationFrameRef.current = requestAnimationFrame(updateProgress);
    }

    const onStateChange = (event: any) => {
        switch (event.data) {
            case 1: // playing
                setIsPlaying(true);
                updateProgress();
                break;
            case 2: // pause
            case 0: // end
                setIsPlaying(false);
                cancelAnimationFrame(animationFrameRef.current!);
                break;
        }
    };

    // 언마운트 시 애니메이션 취소
    useEffect(() => {
        return () => cancelAnimationFrame(animationFrameRef.current!);
    }, []);

    return (
        <>
            <div className="w-full border border-border rounded-xl p-3 flex flex-col gap-2">
                <div className="flex flex-row items-center gap-3">
                    <button className="text-neon-cyan drop-shadow-[0_0_8px_rgba(34,211,238,0.5)] cursor-pointer hover:drop-shadow-[0_0_12px_rgba(34,211,238,0.6)] transition-all duration-200" onClick={togglePlay}>
                        { isPlaying ? <PauseIcon className="size-8"/> : <PlayIcon className="size-8"/> }
                    </button>
                    <div className="flex-1 flex flex-col gap-1">
                        {title && (
                            <span className="text-xs font-semibold text-zinc-200">{title}</span>
                        )}
                        <div className="w-full h-[3px] bg-muted rounded-full">
                            <div className="h-[3px] bg-gradient-to-r from-neon-cyan to-neon-purple shadow-[0_0_8px_rgba(34,211,238,0.5)] rounded-full" style={{ width: `${progress}%` }}></div>
                        </div>
                        <div className="flex justify-end">
                            <span className="text-xs text-zinc-500">{formatTime(currentTimeSec)} / {formatTime(totalDuration)}</span>
                        </div>
                    </div>
                </div>
            </div>
            <div className="hidden">
                <YouTube videoId={embedId} opts={youTubeOptions} onReady={onReady} onStateChange={onStateChange}></YouTube>
            </div>
        </>
    );
}
