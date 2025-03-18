import { useEffect, useRef, useState } from "react";
import PlayIcon from "~/assets/play-circle.svg?react";
import PauseIcon from "~/assets/pause-circle.svg?react";
import YouTube from "react-youtube";

interface MusicPlayerProps {
    embedId: string,
    startTimeSec: number,
    endTimeSec: number,
}

export default function MusicPlayer({ embedId, startTimeSec, endTimeSec }: MusicPlayerProps) {
    const [isPlaying, setIsPlaying] = useState(false);
    const [progress, setProgress] = useState(0);
    const playerRef = useRef<any>(null);
    const animationFrameRef = useRef<number | null>(null);

    // 유튜브 플레이어가 로드될 때 실행
    const onReady = (event: any) => {
        playerRef.current = event.target;
    };

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
            <div className="w-full p-2 flex flex-row items-center gap-2">
                <button className="size-16 cursor-pointer" onClick={togglePlay}>
                    { isPlaying ? <PauseIcon className="size-16"/> : <PlayIcon  className="size-16"/> }
                </button>
                <div className="w-full h-2 bg-zinc-500 rounded-full">
                    <div className="h-2 bg-cyan-400 rounded" style={{ width: `${progress}%` }}></div>
                </div>
            </div>
            <div className="hidden">
                <YouTube videoId={embedId} opts={youTubeOptions} onReady={onReady} onStateChange={onStateChange}></YouTube>
            </div>
        </>
    );
}