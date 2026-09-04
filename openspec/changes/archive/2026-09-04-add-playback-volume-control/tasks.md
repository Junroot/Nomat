## 1. 프론트엔드 — 볼륨 스토어 (`app/stores`)

- [x] 1.1 `app/stores/VolumeStore.ts` 추가 — zustand `create()` + `persist` 미들웨어, localStorage 키 `nomat.volume`. 상태 `{ volume: number, lastAudible: number }`, 액션 `setVolume(v)`·`toggleMute()`. 기본값 둘 다 50. `setVolume`은 0 초과일 때만 `lastAudible`을 갱신하고, `toggleMute`는 `volume > 0 ? 0 : lastAudible`로 전환한다(design Decision 3)
- [x] 1.2 `persist`의 `merge`에서 저장값을 정화한다 — 숫자가 아니거나 0~100 밖이거나 정수가 아니면 기본값으로 대체. `lastAudible`은 1~100 밖이면 50. 주석에 "localStorage는 사용자가 편집할 수 있어 신뢰하지 않는다"를 남긴다
- [x] 1.3 스토어 파일 상단 주석에 "앱의 모든 소리 출처는 이 스토어를 따른다"는 계약과, 음소거를 `volume === 0`으로 표현하는 이유(오케스트레이터의 `mute`/`unMute`와 직교)를 명시한다
- [x] 1.4 사생활 보호 모드 등 localStorage 접근이 실패해도 앱이 동작하는지 확인 — `persist`가 예외를 삼키는지 zustand 5 소스에서 확인하고, 아니면 `createJSONStorage`에 try/catch 래퍼를 준다

## 2. 프론트엔드 — 소리 출처 연결

- [x] 2.1 `app/hooks/useRoundAudioOrchestrator.ts` — `RoundAudioOrchestratorParams`에 `volume: number` 추가. `PLAYBACK_VOLUME` 상수를 제거하고 `volumeRef`(`activeIndexRef` 패턴)로 대체해 네 호출 지점(`makeOnReady`·`startActivePlayback`·`loadCurrentTrack`·REVEAL 재재생)이 `volumeRef.current`를 읽게 한다. 호출 순서(`setVolume` → `playVideo`)는 그대로 둔다
- [x] 2.2 같은 파일에 볼륨 변경 이펙트 추가 — `volume`이 바뀌면 `playersRef.current` **두 플레이어 모두**에 `setVolume`. 주석에 "선버퍼링 쪽은 mute 상태라 소리가 나지 않으며, `setVolume`은 mute를 풀지 않는다"를 남긴다. 훅의 문서 주석에 볼륨이 파라미터로 오는 이유(훅은 스토어를 모른다)를 한 줄 추가한다
- [x] 2.3 `app/components/ui/RoundAudioPlayer.tsx` — `useVolumeStore((s) => s.volume)`을 구독해 오케스트레이터 파라미터로 넘긴다. `RoundAudioPlayerProps`는 `volume`을 받지 않는다(`Omit`) — 방 화면이 볼륨을 알 이유가 없다
- [x] 2.4 `app/components/ui/MusicPlayer.tsx` — `setVolume(50)`을 스토어 값(ref)으로 교체하고, `volume` 변경 시 `playerRef.current?.setVolume(volume)`을 호출하는 이펙트를 추가한다. 이 파일의 다른 부분(`any` 타입·playerVars)은 손대지 않는다(design Decision 6)
- [x] 2.5 diff로 확인 — `useRoundAudioOrchestrator.ts`에서 `mute()`/`unMute()` 호출이 한 줄도 바뀌지 않았는지, `setVolume` 호출 지점이 네 곳 그대로인지

## 3. 프론트엔드 — 볼륨 컨트롤 UI

- [x] 3.1 `app/assets/`에 볼륨 아이콘 SVG 두 개 추가(가청·음소거). 기존 아이콘과 같은 24px 뷰박스·`currentColor` 규약을 따른다
- [x] 3.2 `app/components/ui/VolumeControl.tsx` 추가 — 레일 폭(72px)에 맞는 아이콘 버튼. 마우스를 올리거나 포커스가 들어오면 오른쪽으로 팝오버가 열리고, 팝오버 안에 `<input type="range" min=0 max=100>` + 현재 값. 아이콘 클릭은 음소거 토글이며 `volume === 0`이면 음소거 아이콘. `aria-label`과 range의 `aria-valuenow`를 붙인다
- [x] 3.3 팝오버 접기 — 컨트롤 `mouseleave`·포커스 이탈(`blur`의 `relatedTarget` 검사)·문서 `keydown`(`Escape`, 열려 있는 동안만 등록). 아이콘과 팝오버 사이 간격은 팝오버 래퍼의 padding으로 덮는다. 리스너가 채팅 입력창의 `Enter`/`Shift+Enter` 처리와 간섭하지 않는지 확인한다(`Escape`만 소비)
- [x] 3.4 슬라이더 스타일 — 다크 테마 팔레트(zinc 트랙, `neon-cyan` 썸/채움). 새 색을 도입하지 않는다
- [x] 3.5 `app/components/layout/NavigationBar.tsx` — 하단 슬롯(`VolumeControl` + `Me`)을 직접 렌더링하도록 바꾸고, `AppShell.tsx`의 `MainShell`·`SubShell`에서 `<div className="grow-0 shrink-0"><Me /></div>` 중복을 제거한다. 레일 골격(`hidden md:flex`, 72px)은 그대로다(design Decision 5)
- [x] 3.6 `app/components/ui/AudioGateOverlay.tsx` — 스토어를 읽어 `volume === 0`이면 안내 문구 한 줄("볼륨이 0이에요. 왼쪽 아래 🔊에서 올릴 수 있어요.")을 기존 안내 블록에 추가한다. 볼륨을 바꾸지 않는다(design Decision 7)

## 4. 프론트엔드 — 문서

- [x] 4.1 `front/CLAUDE.md` "상태 관리" 절에 `VolumeStore` 항목 추가 — "앱의 모든 소리 출처는 이 스토어를 구독한다. 새 소리 출처를 만들 때 자체 볼륨 상수를 두지 말 것"
- [x] 4.2 `front/CLAUDE.md` "알려진 함정" 절의 "YouTube IFrame API에는 볼륨 playerVar가 없다" 항목에 "볼륨 값은 `VolumeStore`에서 온다. 재생 개시 지점에서는 ref로 읽어 최신 값을 보장한다"를 덧붙인다. localStorage 키 네임스페이스 `nomat.` 규약도 한 줄 남긴다
- [x] 4.3 `useRoundAudioOrchestrator.ts`의 StrictMode 관련 주석(`RoundAudioPlayer.tsx`의 "볼륨이 컸다가 작아지는" 설명)이 여전히 정확한지 확인하고, 필요하면 "`onReady`가 스토어 값을 읽으므로 두 번째 플레이어도 같은 값을 받는다"를 보강한다

## 5. 프론트엔드 — 검증

- [x] 5.1 수동 시나리오 — 데스크톱: 로비에서 볼륨 20 → 게임 시작 → 게이트 통과 → 라운드 1·2(교대)·REVEAL 재재생 모두 20인지. 게임 중 슬라이더 조작이 즉시 반영되고 재생 위치·반복이 흔들리지 않는지
- [x] 5.2 수동 시나리오 — 음소거: 40에서 음소거 → 0 → 새로고침 → 해제 → 40 복원. 슬라이더로 0까지 내린 뒤 토글 → 40 복원. REVEAL 중 볼륨을 바꿔도 선버퍼링 곡이 들리지 않는지
- [x] 5.3 수동 시나리오 — 볼륨 0 상태로 게임 시작 시 게이트에 안내가 보이고, 통과 후 재생 불가 안내가 뜨지 않는지
- [x] 5.4 수동 시나리오 — 플레이리스트 미리듣기가 설정 볼륨을 따르고 재생 중 변경이 즉시 반영되는지. 플레이리스트 작성 화면 등 소리 없는 페이지에서도 레일에 컨트롤이 보이는지
- [x] 5.5 수동 시나리오 — 모바일 폭(767px 이하)에서 컨트롤이 어디에도 없는지. localStorage에 `"abc"`·`999`를 넣고 새로고침해 50으로 복원되는지
- [x] 5.6 `npx vite preview`로 프로덕션 빌드에서 재생 개시 순간에 기본 볼륨이 새어 나오지 않는지 확인(StrictMode 배제)
- [x] 5.7 `npm run typecheck` 실행 — 통과 확인
- [x] 5.8 `npm run build` 실행 — 통과 확인
