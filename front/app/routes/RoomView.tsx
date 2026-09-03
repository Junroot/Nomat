import { useNavigate, useParams } from "react-router";
import { useCallback, useEffect, useState } from "react";
import AppShell from "~/components/layout/AppShell";
import PlayIcon from "~/assets/play.svg?react";
import PauseCircleIcon from "~/assets/pause-circle.svg?react";
import PlaylistIcon from "~/assets/playlist.svg?react";
import UsersIcon from "~/assets/users.svg?react";
import ColumnsContainer from "~/components/layout/ColumnsContainer";
import Column1 from "~/components/layout/Column1";
import Column2 from "~/components/layout/Column2";
import RoundPanel from "~/components/ui/RoundPanel";
import RoundAudioPlayer from "~/components/ui/RoundAudioPlayer";
import type { ClipPlaybackStatus } from "~/hooks/useClipPlayback";
import AudioGateOverlay from "~/components/ui/AudioGateOverlay";
import PassControl from "~/components/ui/PassControl";
import RoundResultOverlay from "~/components/ui/RoundResultOverlay";
import ChatMessageList from "~/components/ui/ChatMessageList";
import ChatInput from "~/components/ui/ChatInput";
import useBreakpoint from "~/hooks/useBreakpoint";
import useRoomSubscription from "~/hooks/useRoomSubscription";
import useMeStore from "~/stores/MeStore";

export default function RoomView() {
    const { roomId } = useParams();
    const navigate = useNavigate();
    const { isMobile } = useBreakpoint();
    const [showInfo, setShowInfo] = useState(false);

    // 오디오 arming(제스처 게이트 통과) — 방 세션 동안 유지되는 UI 상태(라운드 리듀서 밖).
    const [armed, setArmed] = useState(false);
    // 결과 오버레이 닫힘 여부 — "방으로"로 닫으면 로비 채팅으로 복귀.
    const [resultClosed, setResultClosed] = useState(false);
    // 재생 상태 — 플레이어를 방 화면이 소유하므로 이 상태도 여기서 든다(RoundPanel이 표시만 한다).
    const [playback, setPlayback] = useState<ClipPlaybackStatus>("idle");

    const { roomDetail, players, messages, status, round, isLoading, isDeactivated, sendMessage, startGame, endGame, pass, leaveRoom } = useRoomSubscription(Number(roomId));
    const meId = useMeStore((s) => s.me?.id);

    const isMaster = players.some((p) => p.isMaster && p.id === meId);
    const isPlaying = status === "PLAYING";
    const showGate = isPlaying && !armed && !isDeactivated;
    const showResult = round.phase === "ENDED" && !resultClosed;

    // 자동재생이 차단되면 arming이 소실된 것으로 보고 제스처 게이트를 다시 띄운다.
    const handlePlaybackChange = useCallback((next: ClipPlaybackStatus) => {
        setPlayback(next);
        if (next === "blocked") {
            setArmed(false);
        }
    }, []);

    // 새 게임이 시작되면(phase가 ENDED를 벗어나면) 결과 오버레이 닫힘 상태를 리셋.
    useEffect(() => {
        if (round.phase !== "ENDED") setResultClosed(false);
    }, [round.phase]);
    const actions = isMaster
        ? isPlaying
            ? [{ icon: <PauseCircleIcon />, label: "게임 종료", onClick: endGame }]
            : [{ icon: <PlayIcon />, label: "시작하기", onClick: startGame }]
        : [];

    if (isLoading || !roomDetail) {
        return (
            <AppShell variant="sub" title="로딩 중..." onBack={leaveRoom}>
                <div className="flex items-center justify-center h-full">
                    <div className="size-8 border-2 border-neon-cyan border-t-transparent rounded-full animate-spin" />
                </div>
            </AppShell>
        );
    }

    return (
        <AppShell
            variant="sub"
            title={roomDetail.title}
            onBack={leaveRoom}
            actions={actions}
        >
            <ColumnsContainer>
                {(!isMobile || showInfo) && (
                    <Column1>
                        <p className="text-4xl pt-4 font-bold hidden md:block">{roomDetail.title}</p>
                        <div className="w-full p-3 md:p-4 flex flex-col gap-1 bg-zinc-800 rounded-2xl">
                            <div className="inline-flex items-end gap-1 text-lg md:text-2xl font-bold">
                                <PlaylistIcon className="size-5 md:size-8" />
                                <p>{roomDetail.playlist.title}</p>
                            </div>
                            <p className="text-sm md:text-md text-zinc-400">by. {roomDetail.playlist.master}</p>
                            <p className="text-sm md:text-md mt-2 md:mt-4 text-zinc-200">{roomDetail.playlist.description}</p>
                        </div>
                        <div className="w-full p-3 md:p-4 flex flex-col gap-1 md:gap-2 bg-zinc-800 rounded-2xl">
                            <div className="inline-flex items-end gap-1 text-lg md:text-2xl font-bold">
                                <UsersIcon className="size-5 md:size-8" />
                                <p>플레이어</p>
                                <p className="text-sm md:text-lg">{players.length}</p>
                            </div>
                            {players.map((player) => (
                                <div key={player.id} className="flex items-center gap-2 md:gap-3 p-1.5 md:p-2 hover:bg-zinc-700/50 cursor-pointer rounded-lg transition-colors duration-200">
                                    <div className="relative">
                                        <UsersIcon className="size-7 md:size-10 rounded-full border border-zinc-600" />
                                        <div className="absolute -bottom-0.5 -right-0.5 w-2 h-2 md:w-2.5 md:h-2.5 rounded-full bg-neon-green border-2 border-zinc-800" />
                                    </div>
                                    <div className="flex items-center gap-2">
                                        <p className="text-sm md:text-base text-zinc-200">{player.nickname}</p>
                                        {player.isMaster && (
                                            <span className="bg-neon-purple/20 text-neon-purple text-xs px-1.5 py-0.5 rounded font-semibold">
                                                호스트
                                            </span>
                                        )}
                                    </div>
                                </div>
                            ))}
                        </div>
                    </Column1>
                )}
                <Column2>
                    {isMobile && (
                        <button
                            type="button"
                            className="mx-4 mt-3 px-3 py-2 flex items-center justify-between bg-zinc-800 border border-border rounded-xl text-sm text-zinc-300 cursor-pointer active:bg-zinc-700 transition-colors"
                            onClick={() => setShowInfo((v) => !v)}
                        >
                            <div className="flex items-center gap-2">
                                <UsersIcon className="size-4" />
                                <span>{showInfo ? "채팅으로 돌아가기" : "방 정보 · 플레이어"}</span>
                            </div>
                            <svg xmlns="http://www.w3.org/2000/svg" className={`size-4 text-zinc-500 transition-transform duration-200 ${showInfo ? "rotate-180" : ""}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                <path strokeLinecap="round" strokeLinejoin="round" d="m19.5 8.25-7.5 7.5-7.5-7.5" />
                            </svg>
                        </button>
                    )}
                    {/* PLAYING 중에도 채팅은 계속 노출된다 — 채팅 입력이 곧 정답 추측 채널.
                        정답 공개도 화면을 덮는 오버레이가 아니라 라운드 패널 안에 인라인으로 들어간다.

                        라운드 정보와 정답 영상은 **한 행을 나눠 쓴다.** 영상을 패널 아래 따로 두면
                        폭 전체를 쓰는 띠가 하나 생기고 그 왼쪽은 빈 채로 남아, 화면을 덮지 않을 뿐
                        채팅 영역을 그만큼 잡아먹는다.

                        ⚠️ 이 행 컨테이너는 **조건 없이 렌더된다**(`isPlaying`일 때 className만 바뀐다).
                        `ClipPlayer`의 DOM 부모가 런타임에 사라지면 브라우저가 iframe을 재로드해
                        채워둔 버퍼와 부트스트랩 선불이 통째로 날아가기 때문이다. 같은 이유로
                        `RoundAudioPlayer`는 `RoundPanel` **안이 아니라 형제**여야 한다 — 패널은
                        게임이 끝나면 언마운트된다. */}
                    <div className={isPlaying ? "mx-2 mt-2 flex flex-row items-start gap-2" : "hidden"}>
                        {isPlaying && (
                            <RoundPanel round={round} playback={playback} />
                        )}
                        {/* 오디오 플레이어는 방 세션 자원이다 — `isPlaying` 밖에 두어 **게임 시작 전에**
                            만들고 게임이 끝나도 살려 둔다. 그래야 아이프레임·플레이어 부트스트랩(~460ms)이
                            대기 중에 끝나 1라운드 재생 지연에서 빠진다(대기 중에는 이 컨테이너가
                            `display:none`이지만, 그때도 iframe은 그대로 적재된다 — 지금까지도 플레이어는
                            숨겨진 채로 부트스트랩을 마쳐 왔다).
                            화면에는 정답 공개 구간에만, 그것도 게이트가 내려간 뒤에만 나타난다. */}
                        <RoundAudioPlayer
                            roundNumber={round.roundNumber}
                            phase={round.phase}
                            track={round.currentTrack}
                            nextTrack={round.nextTrack}
                            armed={armed}
                            onPlaybackChange={handlePlaybackChange}
                            videoSuppressed={showGate}
                        />
                    </div>
                    {/* 채팅 피드와 입력창은 각자 상태를 소유한다 — 키 입력이 이 화면을 다시 렌더하지
                        않고, 새 메시지도 피드의 새 항목 하나만 마운트한다. `sendMessage`·`pass`는 참조가
                        고정돼 있으므로 감싸지 말고 그대로 넘긴다(감싸면 `ChatInput`이 매번 렌더된다).

                        포기 컨트롤은 피드 **위에 떠 있다.** 형제로 두면 `Column2`의 `gap-4`가 위아래로
                        붙어, OPEN↔REVEAL마다 피드 높이가 70px씩 흔들려 메시지가 튄다.
                        대신 피드에 하단 패딩(`bottomInset`)을 줘서 떠 있는 버튼이 최신 메시지를 가리지
                        않게 한다 — 패딩은 게임 중 내내 고정이라 라운드 경계에서 레이아웃이 변하지 않는다. */}
                    <div className="relative w-full h-full shrink-1 min-h-0">
                        <ChatMessageList messages={messages} bottomInset={isPlaying} />
                        {/* `OPEN`일 때만 — REVEAL/ENDED에는 포기할 라운드가 없다.
                            인원이 0명이어도 컨트롤 자체는 남는다(발견성).
                            오른쪽 끝은 아래 채팅 입력 pill(`m-2`)에 맞춘다. */}
                        {round.phase === "OPEN" && (
                            <div className="absolute bottom-1 right-2">
                                <PassControl
                                    passedCount={round.passedCount}
                                    requiredCount={round.requiredCount}
                                    passed={round.passed}
                                    onToggle={() => pass(round.roundSeq)}
                                />
                            </div>
                        )}
                    </div>
                    <ChatInput
                        placeholder={isPlaying ? "정답을 입력하세요" : "보낼 메시지 입력"}
                        onSend={sendMessage}
                        passRoundSeq={round.phase === "OPEN" ? round.roundSeq : null}
                        onPass={pass}
                    />
                </Column2>
            </ColumnsContainer>
            {showGate && <AudioGateOverlay onArm={() => setArmed(true)} />}
            {showResult && (
                <RoundResultOverlay round={round} onClose={() => setResultClosed(true)} />
            )}
            {isDeactivated && (
                <div className="fixed inset-0 z-50 flex flex-col items-center justify-center gap-6 bg-zinc-950/80 backdrop-blur-sm">
                    <p className="text-xl font-bold text-zinc-200">다른 탭에서 사용 중입니다</p>
                    <button
                        type="button"
                        className="px-6 py-2.5 rounded-xl bg-neon-cyan/20 text-neon-cyan font-semibold hover:bg-neon-cyan/30 transition-colors cursor-pointer"
                        onClick={() => navigate("/")}
                    >
                        방 목록으로 이동
                    </button>
                </div>
            )}
        </AppShell>
    );
}
