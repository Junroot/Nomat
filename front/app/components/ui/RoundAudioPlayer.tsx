import { useEffect, useRef } from "react";
import ClipPlayer from "~/components/ui/ClipPlayer";
import useClipPlayback, { type ClipPlaybackStatus } from "~/hooks/useClipPlayback";
import type { RoundLifecycle } from "~/hooks/roundReducer";
import type { RoundTrackRef } from "~/utils/RoundEvent";

const PLAYBACK_VOLUME = 50;
const YT_STATE_ENDED = 0;
const YT_STATE_PLAYING = 1;

interface RoundAudioPlayerProps {
    // 라운드 경계 식별자(1-based). 이 값이 바뀌면 재생 담당 플레이어가 교대한다.
    // 첫 라운드 전에는 0이다.
    // ⚠️ `roundSeq`가 아니라 `roundNumber`여야 한다 — `roundSeq`는 전이마다 +1 되는 CAS epoch라
    // REVEAL 진입에서도 바뀌고, 그러면 트랙이 그대로인데 교대가 일어난다.
    roundNumber: number;
    phase: RoundLifecycle;
    // 현재 라운드의 재생 참조. 게임 시작 전에는 null이다.
    track: RoundTrackRef | null;
    // REVEAL 동안 미리 버퍼링할 다음 라운드 트랙. 없거나 마지막 라운드면 null.
    nextTrack: RoundTrackRef | null;
    // 제스처 게이트를 통과했는지 여부. false면 자동재생 정책에 막히므로 재생을 시도하지 않는다.
    armed: boolean;
    // 재생 상태를 상위로 올려 안내 UI가 읽게 한다.
    onPlaybackChange?: (status: ClipPlaybackStatus) => void;
}

/**
 * 라운드 오디오 오케스트레이터. **플레이어 두 개를 라운드마다 교대**시킨다.
 *
 * ```
 *  방 입장            A: 빈 채로 생성 (부트스트랩 선불)  B: 빈 채로 생성
 *  라운드 1 (OPEN)   A: 1번 곡 재생 🔊     B: 유휴
 *  라운드 1 (REVEAL) A: 1번 곡 다시 재생 🔊 B: 2번 곡 선버퍼링 🔇
 *  라운드 2 (OPEN)   A: 정지             B: 2번 곡 재생 🔊  ← 버퍼가 이미 차 있어 즉시 시작
 *  라운드 2 (REVEAL) B: 2번 곡 다시 재생 🔊 A: 3번 곡 선버퍼링 🔇
 * ```
 *
 * 왜 두 개인가: 하나로는 "정답 곡을 REVEAL 내내 들려주기"와 "다음 곡 선버퍼링"을 동시에 할 수
 * 없다. 선버퍼링은 다음 곡을 실제로 적재해 버퍼를 채우는 방식이라(`cueVideoById`는 실측상
 * 버퍼를 채우지 않는다) 현재 곡을 밀어내기 때문이다. 역할을 교대시키면 둘 다 성립한다.
 *
 * 왜 **게임 시작 전에** 만드는가: 아이프레임·플레이어 부트스트랩이 ~460ms인데 이는 어떤 곡을
 * 틀지 몰라도 치를 수 있는 비용이다. 방 대기 중에 끝내두면 1라운드는 로드·버퍼링만 부담한다.
 * (로드·버퍼링은 `embedId`가 곧 정답이라 1라운드 앞에서는 미리 할 수 없다 — 2라운드부터는
 * REVEAL 구간이 그 창을 준다.) 그래서 이 컴포넌트는 `RoundPanel`이 아니라 방 화면이 소유한다.
 *
 * 왜 플레이어를 라운드마다 새로 만들지 않는가: 같은 부트스트랩 비용을 매 라운드 반복하면 그만큼
 * 재생이 늦어지고, 그 손실은 참가자마다 달라 공정성을 해친다. props 동결로 재생성을 막는
 * 방법은 {@link ClipPlayer} 주석 참조.
 *
 * 리마운트가 공짜로 주던 초기화(반복 카운터·재생 판정)는 `roundNumber`를 기준으로 직접 되돌린다 —
 * 카운터는 아래 교대 이펙트가, 재생 판정은 {@link useClipPlayback}이 소유한다.
 *
 * 개발 모드에서 재생 시작 시 볼륨이 컸다가 작아지는 것은 StrictMode 이중 마운트로
 * 플레이어가 두 번 생성되기 때문이며 프로덕션에서는 재현되지 않는다 —
 * 자세한 내용과 판별법은 `front/CLAUDE.md`의 "알려진 함정" 참조.
 */
export default function RoundAudioPlayer({
    roundNumber,
    phase,
    track,
    nextTrack,
    armed,
    onPlaybackChange,
}: RoundAudioPlayerProps) {
    // 라운드 번호의 홀짝으로 담당을 정한다 — 1라운드는 0번, 2라운드는 1번…
    const activeIndex = Math.max(roundNumber - 1, 0) % 2;
    const activeIndexRef = useRef(activeIndex);
    activeIndexRef.current = activeIndex;
    const phaseRef = useRef(phase);
    phaseRef.current = phase;

    const playersRef = useRef<any[]>([null, null]);
    const playsDoneRef = useRef(0);
    const startedRef = useRef(false);
    // 각 플레이어가 현재 물고 있는 트랙. 담당이 될 때 이 값이 맞으면 재적재하지 않는다.
    const loadedEmbedIdRef = useRef<(string | null)[]>([null, null]);
    const loadedRoundRef = useRef(roundNumber);
    const revealReplayedRef = useRef(0);

    const { status, handleReady, handleStateChange, handleError } = useClipPlayback(armed, roundNumber);

    // 자막을 내린다. `cc_load_policy: 0`은 "끄기"가 아니라 **"사용자 설정을 따름"**이라
    // 자막을 켜둔 사용자에게는 그대로 나온다(`1`이 강제로 켜기, `0`은 기본 동작).
    // 확실히 끄려면 captions 모듈 자체를 내려야 한다.
    //
    // 이 메서드는 youtube-player 래퍼가 프록시하지 않지만, 래퍼가 이벤트를 가공 없이
    // 재발행해 `event.target`이 원본 `YT.Player`라 직접 호출할 수 있다.
    // 문서화가 얕은 API라 방어적으로 감싼다 — 실패해도 재생에는 영향이 없다.
    const disableCaptions = (player: any) => {
        try {
            player.unloadModule?.("captions"); // HTML5 플레이어
            player.unloadModule?.("cc"); // 구 AS3 플레이어
        } catch {
            // 지원하지 않는 플레이어 버전 — 자막이 남을 뿐 동작에는 지장이 없다.
        }
    };

    const startActivePlayback = (player: any) => {
        if (startedRef.current) {
            return;
        }
        startedRef.current = true;
        player.unMute();
        player.setVolume(PLAYBACK_VOLUME);
        player.playVideo();
    };

    /**
     * 담당 플레이어에 현재 라운드 트랙을 올린다. 라운드 전환과 플레이어 준비 완료 양쪽에서 쓴다 —
     * 어느 쪽이 먼저 오는지가 상황마다 다르기 때문이다(게임 중 새로고침하면 트랙이 먼저 있고,
     * 대기 중 입장하면 플레이어가 먼저 준비된다).
     */
    const loadCurrentTrack = (player: any, index: number) => {
        if (!track) {
            return;
        }
        // 선버퍼링해둔 그 트랙이라면 **다시 적재하지 않는다** — 재적재하면 채워둔 버퍼를 버려
        // 선버퍼링이 통째로 무의미해진다. 재생 지점만 맞추고 바로 재생한다.
        if (loadedEmbedIdRef.current[index] === track.embedId) {
            player.seekTo(track.startTimeSec, true);
            if (armed) {
                startActivePlayback(player);
            }
            return;
        }

        loadedEmbedIdRef.current[index] = track.embedId;
        const clip = {
            videoId: track.embedId,
            startSeconds: track.startTimeSec,
            endSeconds: track.endTimeSec,
        };
        if (armed) {
            // 적재 후 자동 재생까지 간다 — 게이트를 통과한 세션이므로 별도 playVideo가 필요 없다.
            startedRef.current = true;
            player.unMute();
            player.setVolume(PLAYBACK_VOLUME);
            player.loadVideoById(clip);
        } else {
            // 게이트 미통과 — 적재만 하고 armed 이펙트가 재생을 맡는다.
            player.cueVideoById(clip);
        }
    };

    // 라운드 교대 — 담당 플레이어가 현재 곡을, 상대 플레이어는 정지 상태가 된다.
    useEffect(() => {
        if (roundNumber === loadedRoundRef.current) {
            return;
        }
        loadedRoundRef.current = roundNumber;
        playsDoneRef.current = 0;
        startedRef.current = false;
        revealReplayedRef.current = 0;

        // 직전 라운드를 재생하던 쪽은 조용히 물러난다.
        playersRef.current[1 - activeIndex]?.stopVideo();

        const player = playersRef.current[activeIndex];
        if (player) {
            loadCurrentTrack(player, activeIndex);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [roundNumber, track, armed, activeIndex]);

    // REVEAL 진입 — 담당 플레이어가 정답 곡을 처음부터 다시 들려준다.
    // 타임아웃으로 REVEAL에 오면 클립이 이미 소진돼 있어, 다시 틀지 않으면 아무 소리도 나지 않는다.
    useEffect(() => {
        if (phase !== "REVEAL" || !armed || !track || revealReplayedRef.current === roundNumber) {
            return;
        }
        const player = playersRef.current[activeIndex];
        if (!player) {
            return;
        }
        revealReplayedRef.current = roundNumber;
        player.unMute();
        player.setVolume(PLAYBACK_VOLUME);
        player.seekTo(track.startTimeSec, true);
        player.playVideo();
    }, [phase, roundNumber, track, armed, activeIndex]);

    // 선버퍼링 — REVEAL 동안 **상대 플레이어**가 다음 곡의 버퍼를 채운다.
    useEffect(() => {
        const idleIndex = 1 - activeIndex;
        const player = playersRef.current[idleIndex];
        if (!nextTrack || !player || loadedEmbedIdRef.current[idleIndex] === nextTrack.embedId) {
            return;
        }
        // ⚠️ mute가 먼저다. REVEAL 중 다음 곡이 들리면 그 자체로 정답 유출이다.
        player.mute();
        loadedEmbedIdRef.current[idleIndex] = nextTrack.embedId;
        player.loadVideoById({
            videoId: nextTrack.embedId,
            startSeconds: nextTrack.startTimeSec,
            endSeconds: nextTrack.endTimeSec,
        });
    }, [nextTrack, activeIndex]);

    // 게임이 끝나면 둘 다 멈춘다 — 결과 화면 뒤로 소리가 남지 않게.
    useEffect(() => {
        if (phase !== "ENDED") {
            return;
        }
        playersRef.current.forEach((player) => player?.stopVideo());
    }, [phase]);

    // arming이 라운드 진행 중(플레이어가 이미 ready된 뒤)에 이뤄지면 여기서 재생을 시작한다.
    // 곡이 올라가 있지 않으면(게임 시작 전) 할 일이 없다.
    useEffect(() => {
        const index = activeIndexRef.current;
        const player = playersRef.current[index];
        if (armed && player && loadedEmbedIdRef.current[index] != null) {
            startActivePlayback(player);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [armed]);

    useEffect(() => {
        onPlaybackChange?.(status);
    }, [status, onPlaybackChange]);

    // 볼륨은 메서드로만 제어할 수 있어(playerVars에 volume이 없다) `onReady` 이후에야 정해진다.
    // 그래서 `autoplay` playerVar 대신 여기서 재생을 시작한다 — 그래야 `setVolume`이
    // 재생보다 먼저인 것이 보장된다. IFrame API 공식 문서의 권장 패턴이기도 하다.
    const makeOnReady = (index: number) => (player: any) => {
        playersRef.current[index] = player;
        player.setVolume(PLAYBACK_VOLUME);
        disableCaptions(player);
        // 자동재생 차단 통지는 두 플레이어 모두에 걸어야 한다 — 담당은 라운드마다 교대하므로
        // 한쪽에만 걸면 짝수 라운드에서 차단을 놓친다.
        handleReady(player);

        if (index !== activeIndexRef.current) {
            // 담당이 아닌 쪽은 예열만 해두고 침묵한다.
            player.mute();
            return;
        }
        // 라운드가 이미 진행 중인데 이제야 준비됐다면(게임 중 새로고침) 여기서 곡을 올린다.
        // 게임 시작 전이라면 `track`이 null이라 아무 일도 하지 않는다.
        loadCurrentTrack(player, index);
    };

    const makeOnStateChange = (index: number) => (state: number, player: any) => {
        const isActive = index === activeIndexRef.current;

        if (state === YT_STATE_PLAYING) {
            // captions 모듈은 곡이 바뀔 때 다시 붙을 수 있어 재생이 시작될 때마다 내려준다.
            disableCaptions(player);
        }

        if (!isActive) {
            // 선버퍼링 구간의 전이는 현재 라운드와 무관하다 — 반복 카운터도, 재생 판정도 건드리지 않는다.
            // 판정을 먹이면 다음 라운드의 재생 불가 판정이 관찰도 하기 전에 해제되고,
            // 카운터를 먹이면 이번 라운드의 반복이 잘못 소진된다.
            if (state === YT_STATE_PLAYING) {
                // 버퍼가 찼다 — 소리 없이 여기서 멈춰 담당이 될 때를 기다린다.
                player.pauseVideo();
            }
            return;
        }

        handleStateChange(state);
        if (state !== YT_STATE_ENDED || !track) {
            return;
        }
        if (phaseRef.current === "REVEAL") {
            // 정답 공개 구간에는 클립을 **계속 돌린다.** 한 번만 틀면 클립이 REVEAL(5초)보다 짧을 때
            // 도중에 끝나 버리고, 그러면 소리가 끊길 뿐 아니라 유튜브가 영상 한가운데에
            // 일시정지 아이콘을 띄운 채로 남긴다(iframe 내부라 CSS로 지울 수 없다).
            // 반복 카운터는 건드리지 않는다 — 라운드의 재생 횟수와는 무관한 재생이다.
            player.seekTo(track.startTimeSec, true);
            player.playVideo();
            return;
        }
        if (phaseRef.current !== "OPEN") {
            return;
        }
        playsDoneRef.current += 1;
        if (playsDoneRef.current < track.repeatCount) {
            player.seekTo(track.startTimeSec, true);
            player.playVideo();
        }
    };

    // 영상은 정답 공개 구간의 **담당 플레이어만** 드러낸다.
    // - `OPEN`에 드러내면 썸네일·제목이 곧 정답이라 게임이 성립하지 않는다
    // - 선버퍼링 중인 상대 플레이어는 다음 곡을 물고 있으므로 REVEAL에도 계속 숨긴다
    const revealing = phase === "REVEAL";

    return (
        <>
            <ClipPlayer
                visible={revealing && activeIndex === 0}
                onReady={makeOnReady(0)}
                onStateChange={makeOnStateChange(0)}
                onError={handleError}
            />
            <ClipPlayer
                visible={revealing && activeIndex === 1}
                onReady={makeOnReady(1)}
                onStateChange={makeOnStateChange(1)}
                onError={handleError}
            />
        </>
    );
}
