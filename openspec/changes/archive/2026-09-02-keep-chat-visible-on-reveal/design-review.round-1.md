# design.md 적대적 리뷰 — keep-chat-visible-on-reveal

검증 통과한 `[치명]`·`[높음]` 지적 없음.

`design.md`의 코드 주장을 `RoomView.tsx`·`ClipPlayer.tsx`·`RoundRevealOverlay.tsx`·`RoundAudioPlayer.tsx`·`RoundPanel.tsx`·`Column2.tsx`·`ColumnsContainer.tsx`·`AppShell.tsx`·`useBreakpoint.ts`·`roundReducer.ts`·`useRoomSubscription.ts`·`useRoundAudioOrchestrator.ts`·`app.css`, 그리고 `RoundService.kt`·`RoundStateStoreImpl.kt`에서 직접 확인했다. 각 Decision의 전제가 모두 현재 코드와 일치했고, 진입을 막는 반례를 세우지 못했다. 관찰 사실은 `design-review-verified.md`에 있다.

## 기각한 후보

아래는 의심했으나 코드·문서로 **반증된** 항목이다. 지적이 아니다.

### 1. "`REVEAL`인데 `status !== PLAYING`인 창이 있어, 정답 표시를 `isPlaying &&` 안의 `RoundPanel`로 옮기면 정답이 아예 안 보인다"

Decision 1은 정답 표시를 `RoundPanel`로 옮기는데, `RoundPanel`은 `{isPlaying && …}`(`RoomView.tsx:186`)이고 현재 `RoundRevealOverlay`는 `showReveal = phase === "REVEAL" && !showGate`(`RoomView.tsx:70`)로 `isPlaying`과 무관하게 뜬다. 게이팅이 좁아지므로 두 값이 어긋나는 창을 찾았으나 없었다.

- `useRoomSubscription.ts:139-159` — `STARTED`는 `setStatus("PLAYING")`와 `dispatchRound(GAME_STARTED)`를, `ENDED`는 `setStatus("ACTIVE")`와 `dispatchRound(GAME_ENDED)`를 **같은 이벤트 핸들러 안에서 연속 호출**한다. React 18 자동 배칭으로 한 커밋에 반영되므로 `phase === "REVEAL" && status !== "PLAYING"` 중간 상태가 렌더되지 않는다.
- `useRoomSubscription.ts:215-225` — 재접속 하이드레이션도 `setStatus(detail.status)`와 `dispatchRound(HYDRATE)`가 같은 `.then()` 안이다.
- 게임 종료가 `REVEAL` 중에 와도 `phase`는 `REVEAL → ENDED`로 함께 넘어가고(`roundReducer.ts:110`), 그 시점의 화면은 스코프 밖인 `RoundResultOverlay`가 받는다.

### 2. "모바일 `showInfo` 토글이 `Column1`을 마운트/언마운트할 때 형제인 `Column2`가 리마운트되어 플레이어가 재생성된다"

Decision 2의 핵심 전제("`Column2`는 조건 없이 렌더된다")를 깨려 했으나 성립하지 않는다. `ColumnsContainer`의 JSX 자식은 `{(!isMobile || showInfo) && <Column1>…}`와 `<Column2>` 두 표현식이고(`RoomView.tsx:132-133,169`), React는 이 고정 길이 자식 배열을 인덱스로 재조정한다. 첫 표현식이 `false ↔ <Column1>` 사이를 오가도 인덱스 1의 `Column2` 인스턴스는 보존된다. `Column2`·`ColumnsContainer`에 `key`도 없다.

### 3. "`ClipPlayer`를 흐름 안으로 옮기면 `useRoundAudioOrchestrator`의 명령형 제어가 깨진다"

`useRoundAudioOrchestrator.ts` 전체(308줄)를 열어 확인했다. DOM 참조·요소 크기 측정·`document` 접근이 전혀 없고, 제어는 전부 `playersRef`에 담은 player 핸들 메서드 호출이다. 컨테이너의 `position`·크기는 이 훅의 어떤 분기에도 들어가지 않는다. `RoundAudioPlayer`도 Fragment 안에 `ClipPlayer` 둘을 그릴 뿐 래퍼 DOM이 없어(`RoundAudioPlayer.tsx:40-48`), 두 `ClipPlayer` div가 그대로 `Column2`의 flex 항목이 된다.

### 4. "delta spec 안에서 시나리오가 서로 모순된다 — `REVEAL`에 전체화면 오버레이 금지 vs 게이트가 뜬 채 `REVEAL`"

`specs/room-round-ui/spec.md`의 두 시나리오를 대조했으나 모순이 아니다. "정답 공개가 채팅을 덮지 않는다"의 WHEN은 "`REVEAL`로 전이되어 **영상과** 정답·승자·점수판이 표시됨"이고, 게이트 표시 중에는 같은 요구사항이 영상 표시를 금지하므로(같은 delta의 게이트 불릿·시나리오) 이 WHEN 전제가 성립하지 않는다. 금지 문장도 "정답 공개 UI가"로 주체가 한정돼 있고, design.md `## Non-Goals`가 게이트·다중 탭 오버레이의 전체화면 유지를 명시적으로 스코프 아웃한다.

### 5. "delta spec의 MODIFIED 헤딩이 본 스펙과 달라 적용되지 않는다"

`openspec/specs/room-round-ui/spec.md`의 `### Requirement:` 헤딩 9개와 대조했다. delta의 두 헤딩("게임 중 채팅 입력이 정답 추측이며 별도 정답 입력 UI가 없다", "ROUND_REVEALED 수신 시 정답·승자·점수판을 표시한다")이 문자열까지 일치하고, delta는 기존 본문·시나리오를 모두 보존한 위에 게이트 불릿·화면 점유 금지·스크롤 유지 문단과 시나리오 4개를 더한다. 삭제된 기존 시나리오는 없다.

### 6. "`display:none` 요소가 `Column2`의 `gap-4`를 차지해 `REVEAL`이 아닌 동안에도 빈 공간이 생긴다"

Decision 2의 단정을 검증했다. `Column2`는 `flex flex-col … gap-4`(`Column2.tsx:5`)이고 두 `ClipPlayer`의 비노출 클래스는 `"hidden"`(`ClipPlayer.tsx:82-83`) 하나뿐이다. `display: none` 요소는 박스를 생성하지 않아 flex 항목이 아니므로 `gap`의 대상이 되지 않는다. `REVEAL` 동안에도 노출되는 것은 `revealing && activeIndex === index`인 한 개뿐이라(`RoundAudioPlayer.tsx:45`) 나머지 하나가 빈 gap을 만들지도 않는다.

### 7. "정답 곡 재생·선버퍼링이 게이트 미통과 상태에서 영상만 숨기면 어긋난다"

Decision 5가 "표시 여부만 바꾼다"고 단정한 부분이다. `useRoundAudioOrchestrator.ts:176-189`의 REVEAL 재생 이펙트는 이미 `armed`를 조건으로 갖고 있어 게이트 미통과 중에는 애초에 재생이 시작되지 않고, 선버퍼링(`:191-206`)과 `ENDED` 정지(`:209-214`)는 `visible`과 무관한 별개 이펙트다. 노출 억제 플래그는 `ClipPlayer`의 `visible` 계산에만 닿으므로 재생 경로에 영향이 없다.

판정: 진입 가능 — 모든 Decision의 코드 전제를 실제 파일에서 확인했고, 문서 간(design·proposal·tasks·spec) 불일치나 진입을 막는 결함을 찾지 못했다.
