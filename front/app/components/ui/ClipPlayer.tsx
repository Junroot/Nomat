import { useMemo } from "react";
import YouTube, { type YouTubeEvent } from "react-youtube";
import type { YouTubePlayerHandle } from "~/utils/youtubePlayer";

interface ClipPlayerProps {
    // 영상을 화면에 드러낼지. 정답 공개 구간의 담당 플레이어만 true다.
    visible: boolean;
    // ⚠️ 콜백은 react-youtube의 이벤트가 아니라 **재생 메서드를 가진 player 객체**를 받는다.
    // 이벤트 객체에는 `playVideo`·`unMute` 같은 메서드가 없다(`event.target`에 있다).
    // 이 컴포넌트가 그 차이를 흡수해 호출부가 이벤트 형태를 몰라도 되게 한다.
    onReady: (player: YouTubePlayerHandle) => void;
    onStateChange: (state: number, player: YouTubePlayerHandle) => void;
    onError?: () => void;
}

// 곡 없이 생성한다. 어떤 곡을 틀지는 `useRoundAudioOrchestrator`가 `loadVideoById`로 정한다.
const EMPTY_VIDEO_ID = "";

/**
 * YouTube 플레이어 인스턴스 하나를 감싸는 프리미티브. **상태를 갖지 않고 명령을 받지도 않는다** —
 * 재생 제어는 전적으로 `onReady`로 넘긴 player 객체를 통해 `useRoundAudioOrchestrator`가 한다.
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
                      // 브라우저가 이를 재로드해 채워둔 버퍼와 재생 상태가 날아간다. 그래서 이
                      // 컴포넌트는 언마운트되지 않는 자리(`Column2`)에 처음부터 마운트돼 있고,
                      // 여기서는 **제자리에서 보이기만** 한다. 부모가 고정돼 있으므로 흐름 안에
                      // 그대로 있어도 되고, `fixed`로 화면에 띄울 이유가 없다.
                      //
                      // 이 구간의 주된 페이로프는 계속 재생되는 정답 곡의 소리이고 영상은 "아 이
                      // 곡이었구나"를 확인하는 수단이다. 그래서 화면을 점유하지 않는 축소 박스로,
                      // **라운드 정보 옆자리**에 붙는다 — 아래에 따로 두면 폭 전체를 쓰는 띠가 생겨
                      // 그만큼 채팅 영역이 줄어든다.
                      //
                      // 폭은 **고정 px이 아니라 같은 행에 대한 비율**이다. 고정 px으로 두면 좁은
                      // 데스크톱(768~900px)에서 `Column2`가 `flex-2`로 줄어드는 동안 영상만 제 폭을
                      // 고수해 라운드 패널을 짓누른다 — 실측에서 뷰포트 768px일 때 패널 173px 대
                      // 영상 256px로 영상이 더 넓어졌다. 비율로 두면 어느 폭에서도 영상이 행의
                      // 40%를 넘지 않고, 넓은 화면에서는 `max-w`가 과도한 확대를 막는다.
                      //
                      // 유튜브 제목·로고를 억지로 가리지 않는다. 이 구간에는 정답이 이미 공개돼
                      // 있어 제목이 보여도 유출이 아니고, 가리려면 iframe을 키워 가장자리를
                      // 잘라내야 하는데 그만큼 영상이 작아진다. 가리는 대신 크게 보여주는 쪽을 택했다.
                      //
                      // iframe은 react-youtube가 만들어 붙이므로 클래스를 직접 줘도 확실하지 않다.
                      // 자손 선택자로 절대 배치해 컨테이너를 확실히 채운다 — 그 기준이 되도록
                      // 컨테이너에 `relative`를 준다(예전에는 `fixed`가 그 역할을 겸했다).
                      // `pointer-events-none`: 클릭이 통과하게 해 영상을 눌러 재생을 멈추는 사고를 막는다.
                      "relative shrink-0 w-[38%] max-w-[26rem] aspect-video" +
                      " overflow-hidden rounded-2xl bg-black ring-1 ring-neon-cyan/30 shadow-2xl pointer-events-none" +
                      " [&_iframe]:absolute [&_iframe]:inset-0 [&_iframe]:h-full [&_iframe]:w-full"
                    : // `hidden`(display:none)은 iframe을 재로드하지 않는다 — 소리는 그대로 난다.
                      // display:none 요소는 flex 항목이 아니므로 `Column2`의 gap에도 잡히지 않아,
                      // 공개 구간이 아닌 동안에는 공간을 전혀 차지하지 않는다.
                      "hidden"
            }
        >
            <YouTube
                videoId={EMPTY_VIDEO_ID}
                opts={options}
                // `event.target`은 react-youtube의 타입상 `any`다(아래 타입 모듈 주석 참조).
                // 경계가 여기 한 곳뿐이므로 넘기는 지점에서 우리 타입으로 좁혀 고정한다.
                onReady={(event: YouTubeEvent) => onReady(event.target as YouTubePlayerHandle)}
                onStateChange={(event: YouTubeEvent<number>) =>
                    onStateChange(event.data, event.target as YouTubePlayerHandle)
                }
                onError={onError}
            />
        </div>
    );
}
