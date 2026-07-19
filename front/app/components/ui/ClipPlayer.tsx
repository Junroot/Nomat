import { useMemo } from "react";
import YouTube from "react-youtube";

interface ClipPlayerProps {
    // 영상을 화면에 드러낼지. 정답 공개 구간의 담당 플레이어만 true다.
    visible: boolean;
    // ⚠️ 콜백은 react-youtube의 이벤트가 아니라 **재생 메서드를 가진 player 객체**를 받는다.
    // 이벤트 객체에는 `playVideo`·`unMute` 같은 메서드가 없다(`event.target`에 있다).
    // 이 컴포넌트가 그 차이를 흡수해 호출부가 이벤트 형태를 몰라도 되게 한다.
    onReady: (player: any) => void;
    onStateChange: (state: number, player: any) => void;
    onError?: () => void;
}

// 곡 없이 생성한다. 어떤 곡을 틀지는 `RoundAudioPlayer`가 `loadVideoById`로 정한다.
const EMPTY_VIDEO_ID = "";

/**
 * YouTube 플레이어 인스턴스 하나를 감싸는 프리미티브. **상태를 갖지 않고 명령을 받지도 않는다** —
 * 재생 제어는 전적으로 `onReady`로 넘긴 player 객체를 통해 호출부(`RoundAudioPlayer`)가 한다.
 *
 * **곡을 지정하지 않고 만든다.** 그래야 방에 들어온 시점에 — 어떤 곡이 나올지 알기 전에 —
 * 아이프레임·플레이어 부트스트랩(~460ms)을 미리 끝낼 수 있고, 그 비용이 1라운드 재생 지연에서
 * 빠진다. 곡은 라운드가 열릴 때 `loadVideoById`로 넣는다.
 *
 * 이 컴포넌트가 존재하는 이유는 **props 동결**이다. react-youtube(10.1.0)는 `videoId`가
 * 바뀌거나 `opts`가 깊은 비교로 달라지면 플레이어를 파괴하고 다시 만들며(`shouldResetPlayer`),
 * `componentDidUpdate`는 리셋 직후 반환해 `updateVideo()`에 도달조차 하지 않는다. 위 부트스트랩을
 * 미리 해두는 의미가 사라지므로, 두 props를 상수로 붙들어 두고 곡 교체는 전부 명령형으로 처리한다.
 *
 * `start`/`end` playerVar를 두지 않는 이유: 클립 경계는 모든 라운드에서 `loadVideoById`의
 * `startSeconds`/`endSeconds`가 잡는다. playerVar는 생성 시점에 곡을 아는 경우에만 쓸 수 있어
 * 여기서는 성립하지 않고, 없애면 "첫 라운드만 다르게 동작하는" 특례도 함께 사라진다.
 */
export default function ClipPlayer({ visible, onReady, onStateChange, onError }: ClipPlayerProps) {
    // `controls: 0`은 하단 컨트롤 바를 없앤다(중앙 일시정지 표시는 별개라 영향받지 않는다).
    // `modestbranding`·`showinfo`는 유튜브가 각각 2023·2018년에 무효화·제거해 지금은 효과가 없다 —
    // 호환을 위해 남겨두되 이것들로 브랜딩이 사라질 것을 기대하지 않는다.
    const options = useMemo(
        () => ({
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
            },
        }),
        [],
    );

    return (
        <div
            className={
                visible
                    ? // 노출은 DOM 이동이 아니라 CSS로만 한다 — iframe을 다른 부모로 옮기면
                      // 브라우저가 이를 재로드해 채워둔 버퍼와 재생 상태가 날아간다.
                      // 그래서 오버레이 안으로 넣지 않고 제자리에서 fixed로 띄운다.
                      //
                      // ⚠️ 위치·크기는 `RoundRevealOverlay`의 상단 여백과 같은 식으로 맞물려 있다.
                      // 한쪽만 바꾸면 겹치거나 사이가 벌어진다.
                      //
                      // 유튜브 제목·로고를 억지로 가리지 않는다. 이 구간에는 정답이 이미 공개돼
                      // 있어 제목이 보여도 유출이 아니고, 가리려면 iframe을 키워 가장자리를
                      // 잘라내야 하는데 그만큼 영상이 작아진다. 가리는 대신 크게 보여주는 쪽을 택했다.
                      //
                      // 폭은 세 상한 중 최솟값이다 — 화면 폭(92vw), 절대 상한(720px),
                      // 그리고 **세로 상한(80vh를 16:9로 환산한 값)**. 세 번째가 없으면 낮고 넓은
                      // 화면에서 영상이 세로를 다 먹어 정답 텍스트를 밀어낸다.
                      //
                      // iframe은 react-youtube가 만들어 붙이므로 클래스를 직접 줘도 확실하지 않다.
                      // 자손 선택자로 절대 배치해 컨테이너를 확실히 채운다.
                      // `pointer-events-none`: 클릭이 통과하게 해 영상을 눌러 재생을 멈추는 사고를 막는다.
                      "fixed left-1/2 top-[8vh] -translate-x-1/2 z-50 w-[min(92vw,720px,80vh)] aspect-video" +
                      " overflow-hidden rounded-2xl bg-black ring-1 ring-neon-cyan/30 shadow-2xl pointer-events-none" +
                      " [&_iframe]:absolute [&_iframe]:inset-0 [&_iframe]:h-full [&_iframe]:w-full"
                    : // `hidden`(display:none)은 iframe을 재로드하지 않는다 — 소리는 그대로 난다.
                      "hidden"
            }
        >
            <YouTube
                videoId={EMPTY_VIDEO_ID}
                opts={options}
                onReady={(event: any) => onReady(event.target)}
                onStateChange={(event: any) => onStateChange(event.data, event.target)}
                onError={onError}
            />
        </div>
    );
}
