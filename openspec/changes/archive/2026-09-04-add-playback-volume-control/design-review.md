# design-review — add-playback-volume-control (라운드 1)

검증 통과 지적 없음. `design.md`의 코드 주장을 `useRoundAudioOrchestrator.ts`·`MusicPlayer.tsx`·`RoundAudioPlayer.tsx`·`ClipPlayer.tsx`·`NavigationBar.tsx`·`AppShell.tsx`·`AudioGateOverlay.tsx`·`ChatInput.tsx`·`useClipPlayback.ts`·`useBreakpoint.ts`·zustand 5.0.3 `persist` 소스·메인 스펙 `room-round-ui`와 대조했고, `proposal.md`·`tasks.md`·`specs/**/spec.md` 사이의 불일치도 찾지 못했다. 관찰 사실은 `design-review-verified.md`에 남겼다.

## 기각한 후보

- **"`persist`가 storage 예외를 삼킨다"는 주장이 부분적으로만 사실** — `createJSONStorage`는 `getStorage()`(=`localStorage` 접근) 예외와 `getItem`/`JSON.parse` 예외는 삼키지만(`toThenable`), `setItem` 예외는 삼키지 않는다(`node_modules/zustand/esm/middleware.mjs`). 그러나 사생활 보호 모드·사이트 데이터 차단에서 실제로 발생하는 실패는 `localStorage` 접근 자체의 `SecurityError`라 삼켜지는 경로에 해당하고, 설계는 이 주장 뒤에 "태스크에서 실제로 확인한다"를 붙였으며 `tasks.md` 1.4가 "아니면 `createJSONStorage`에 try/catch 래퍼를 준다"는 대응을 이미 포함한다. 설계가 스스로 열어둔 검증 항목이므로 결함으로 세우지 않는다.
- **"`persist`가 스토어 생성 시점에 동기 hydration한다"** — `persistImpl`이 생성 직후 `hydrate()`를 호출하고, 동기 storage면 `toThenable` 체인이 즉시 실행돼 `set(merged, true)`까지 동기로 끝난 뒤 `stateFromStorage`를 반환한다. 주장대로다.
- **"`setVolume`은 mute를 풀지 않는다 / `unMute()`를 해도 볼륨 0이면 무음"(Decision 3·4)** — 공식 레퍼런스는 `getVolume()`이 mute와 무관하게 볼륨을 돌려준다고만 적고 상호작용을 규정하지 않는다. 반례를 세울 근거를 찾지 못했고, 실제 unMute 세 지점의 순서가 `unMute()` → `setVolume()` → 재생(`useRoundAudioOrchestrator.ts:112-114,145-147,185-188`)이라 unMute가 볼륨을 되돌리더라도 직후 `setVolume(volumeRef.current)`이 덮는다. 선버퍼링 쪽 유출 여부는 `tasks.md` 5.2·스펙 시나리오("선버퍼링 플레이어의 음소거는 볼륨 변경에 풀리지 않는다")가 실측 항목으로 잡고 있다.
- **볼륨 변경 이펙트가 `RoundAudioPlayer`를 슬라이더 틱마다 재렌더해 플레이어가 재생성될 위험** — `ClipPlayer`의 `opts`는 `useMemo(..., [])`, `videoId`는 상수라 react-youtube의 `shouldResetPlayer`가 참이 되지 않는다(`ClipPlayer.tsx:40-55`). 콜백 props는 이미 매 렌더 새로 넘어가고 있어 새로 생기는 조건이 아니다.
- **팝오버 `Escape` 리스너와 채팅 입력 키 처리의 간섭** — `ChatInput`의 전역 리스너는 `Enter`만 보고(`ChatInput.tsx:31-40`) `Escape`는 어디서도 소비하지 않는다. 충돌 경로 없음.
- **`room-round-ui` 델타가 `ADDED`인데 기존 요구사항과 충돌하는지** — 메인 스펙에 볼륨 값을 규정한 요구사항이 없다(전체 grep). 선버퍼링 음소거·REVEAL 재재생 요구는 델타가 인용한 대로 존재하며 새 요구사항과 모순되지 않는다.
- **"첫 localStorage 사용"** — `front/app` 전체에 `localStorage`/`sessionStorage` 사용처 없음. 주장대로다.
- **로그인 화면 예외** — `LoginView.tsx`는 `AppShell`/`NavigationBar`를 쓰지 않아 스펙의 "레일 자체가 없는 화면은 예외"가 성립한다.

판정: 진입 가능 — 설계의 코드 주장(상수 4곳·unMute 3곳·mute 2곳·ref 패턴·셸 중복·hydration 동기성)이 현재 코드와 일치하고, 산출물 간 불일치와 누락된 부수 효과 경로를 찾지 못했다.
