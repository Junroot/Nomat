import { useEffect } from "react";
import ClipPlayer from "~/components/ui/ClipPlayer";
import useRoundAudioOrchestrator, {
    PLAYER_COUNT,
    type RoundAudioOrchestratorParams,
} from "~/hooks/useRoundAudioOrchestrator";
import type { ClipPlaybackStatus } from "~/hooks/useClipPlayback";

interface RoundAudioPlayerProps extends RoundAudioOrchestratorParams {
    // 재생 상태를 상위로 올려 안내 UI가 읽게 한다.
    onPlaybackChange?: (status: ClipPlaybackStatus) => void;
}

/**
 * 라운드 오디오 플레이어. 플레이어 두 개를 그려두고, 이들의 교대·선버퍼링·REVEAL 재생은
 * {@link useRoundAudioOrchestrator}에 맡긴다 — 이 컴포넌트가 직접 하는 일은 렌더링뿐이다.
 * 왜 두 개인지, 왜 라운드마다 새로 만들지 않는지는 그 훅의 주석에 있다.
 *
 * 왜 **게임 시작 전에** 만드는가: 아이프레임·플레이어 부트스트랩이 ~460ms인데 이는 어떤 곡을
 * 틀지 몰라도 치를 수 있는 비용이다. 방 대기 중에 끝내두면 1라운드는 로드·버퍼링만 부담한다.
 * (로드·버퍼링은 `embedId`가 곧 정답이라 1라운드 앞에서는 미리 할 수 없다 — 2라운드부터는
 * REVEAL 구간이 그 창을 준다.) 그래서 이 컴포넌트는 `RoundPanel`이 아니라 방 화면이 소유한다.
 *
 * 개발 모드에서 재생 시작 시 볼륨이 컸다가 작아지는 것은 StrictMode 이중 마운트로
 * 플레이어가 두 번 생성되기 때문이며 프로덕션에서는 재현되지 않는다 —
 * 자세한 내용과 판별법은 `front/CLAUDE.md`의 "알려진 함정" 참조.
 */
export default function RoundAudioPlayer({ onPlaybackChange, ...params }: RoundAudioPlayerProps) {
    const { activeIndex, status, playerHandlers } = useRoundAudioOrchestrator(params);

    useEffect(() => {
        onPlaybackChange?.(status);
    }, [status, onPlaybackChange]);

    // 영상은 정답 공개 구간의 **담당 플레이어만** 드러낸다.
    // - `OPEN`에 드러내면 썸네일·제목이 곧 정답이라 게임이 성립하지 않는다
    // - 선버퍼링 중인 상대 플레이어는 다음 곡을 물고 있으므로 REVEAL에도 계속 숨긴다
    const revealing = params.phase === "REVEAL";

    return (
        <>
            {Array.from({ length: PLAYER_COUNT }, (_, index) => (
                // `key`는 인덱스로 고정한다 — 두 플레이어는 순서가 바뀌지도, 늘거나 줄지도 않는다.
                // 여기서 key가 흔들리면 플레이어가 재생성돼 부트스트랩 선불이 통째로 무의미해진다.
                <ClipPlayer key={index} visible={revealing && activeIndex === index} {...playerHandlers(index)} />
            ))}
        </>
    );
}
