## 1. Modal 프리미티브 재작성 (front/)

- [x] 1.1 `app/components/ui/Modal.tsx` — 내부 상태를 `visible`/`closing` 두 boolean에서 `phase: "closed" | "open" | "closing"` 단일 상태로 교체. `phase`는 **열림 여부를 결정하지 않고** 지연 언마운트만 담당한다(design.md Decision 1). `isOpen` 변화에 따른 전이: `false→true`면 `open`, `true→false`면 `closing`, 퇴장 완료 시 `closed`. 닫히는 중 `isOpen`이 다시 `true`가 되면 `open`으로 스냅백(현재 앱 조작으로는 도달 불가 — 배경이 비활성이므로. 상태 머신의 **방어적 전이**로만 둔다, design.md 상태 머신 절)
- [x] 1.2 오버레이를 커스텀 `div` → 네이티브 `<dialog>` + `showModal()`로 교체. `<dialog>` 자체에는 **패딩·배경·크기를 주지 않고**(배경 클릭 판정이 패딩에서 오판되지 않도록) 안쪽 래퍼 div가 기존 시각(`bg-surface border border-border p-6 rounded-2xl shadow-glow-cyan max-h-[90vh] overflow-y-auto mx-4`)을 담당. 기존 배경 스타일(`bg-black/60 backdrop-blur-sm`)은 `::backdrop`으로 이식
- [x] 1.3 `showModal()`/`close()` 호출에 **StrictMode 이중 이펙트 가드** — `if (!dialog.open) dialog.showModal()`, `if (dialog.open) dialog.close()`(`front/CLAUDE.md`의 "StrictMode와 명령형 서드파티 플레이어" 함정과 동일 계열, design.md Decision 2)
- [x] 1.4 배경 클릭 → `dismissible`이면 `onClose()` 호출, 아니면 무시. 판정은 `e.target === dialogRef.current`(래퍼 내부 클릭은 자연히 제외되므로 `stopPropagation` 불필요). **모달이 스스로 열림 상태를 바꾸지 않는다**
- [x] 1.5 ESC → `<dialog>`의 `cancel` 이벤트를 구독. `dismissible`이면 `preventDefault()` 후 `onClose()` 호출, `dismissible`이 `false`면 `preventDefault()`만 한다(**최선 노력** — 활성화가 없으면 플랫폼이 무시한다, design.md Decision 2 함정 4)
- [x] 1.6 **네이티브 `close` 이벤트 구독(신규)** — `<dialog>`가 실제로 닫히면(브라우저 강제 닫힘 포함) `phase="closed"`로 내리고 `onClose()`를 호출해 부모 `isOpen`을 동기화한다. 부모 주도의 정상 닫힘 경로(1.7의 `close()` 호출)에서 `onClose`가 **중복 호출되지 않도록** "내가 닫는 중"임을 ref로 가드(design.md Decision 2 함정 4)
- [x] 1.7 퇴장 완료 판정 — `animationend`(`e.currentTarget === e.target` 가드 유지) **또는** 타임아웃(애니메이션 길이 0.2s + 여유) 중 **먼저 오는 쪽**으로 `close()` + `phase="closed"`. 양쪽 신호·타이머를 언마운트/재오픈 시 정리(design.md Decision 3)
- [x] 1.8 `dismissible?: boolean` prop 신규(기본 `true`) — 닫기 억제를 선언적으로 표현. `onClose`의 "애니메이션 종료 통보" 의미는 **제거**하고, `ModalProps` JSDoc에 새 계약을 명시: "`onClose`는 **무조건 이행**해야 하는 닫기 신호다(브라우저 강제 닫힘 시에도 발생). 닫기를 막으려면 `onClose`를 no-op으로 두지 말고 `dismissible={false}`를 쓴다. `dismissible={false}`의 ESC 억제는 최선 노력이다"
- [x] 1.9 `phase === "closed"`면 children을 렌더하지 않는다 — 재오픈은 언제나 `closed` 이후이므로 **이 규칙이 재오픈 리셋의 실질적 근거**다. 여기에 더해 **열림마다 증가하는 내부 키**를 children 래퍼에 부여해, 스냅백(방어적 전이)에서도 같은 결과가 나오게 한다. 이 보장은 **children 서브트리 한정**이며(state를 `Modal` 위에 두는 소비자는 스스로 리셋한다 — 4.7), 그 계약 경계를 `ModalProps` JSDoc에 명시한다(design.md Decision 4)
- [x] 1.10 배경 스크롤 잠금 — 모듈 수준 **참조 카운터**로 `document.body` overflow 제어. 마지막 모달이 닫힐 때만 복원
- [x] 1.11 `ModalTitle` 신규 — `Modal`이 `useId()`로 만든 id를 컨텍스트로 내려주고 `ModalTitle`이 `<h2 id={titleId}>`로 렌더, `<dialog aria-labelledby={titleId}>`가 참조. 제목 스타일을 여기로 통일(design.md Decision 6)
- [x] 1.12 `initialFocusRef?: RefObject<HTMLElement | null>` prop 신규 — `showModal()` 호출 **직후** ref가 가리키는 요소에 `focus()`를 호출해 초기 포커스를 고정한다(ref가 없거나 대상이 아직 없으면 브라우저 기본 규칙에 맡긴다). **React의 `autoFocus` prop을 쓰지 않는다** — React는 `autofocus` 속성을 DOM에 내보내지 않고 commit 시 `focus()` 폴리필로 대체하는데, 그 시점의 `<dialog>`는 `showModal()` 전이라 `display:none`이어서 무효다(design.md Decision 2 함정 3)
- [x] 1.13 **토스트 재승격 신호(신규)** — `showModal()` 직후 전역 토스트 컨테이너에 재승격 신호를 보낸다(커스텀 이벤트 또는 전용 훅). top layer는 승격 **순서**로 쌓여, 앱 마운트 때 올려둔 토스트가 나중에 열린 다이얼로그 아래에 깔리기 때문이다. 모달이 연속으로 열려도 항상 마지막 모달 위에 오도록 **매 열림마다** 보낸다(design.md Decision 8, 5절)

## 2. 애니메이션·동작 축소 대응 (front/)

- [x] 2.1 `app/app.css` — `::backdrop` 페이드 인/아웃 유틸 추가(기존 `fade-in`/`fade-out` 키프레임 재사용). 모달 패널은 기존 `animate-scale-in`/`animate-scale-out` 유지
- [x] 2.2 `@media (prefers-reduced-motion: reduce)`에서 모달 관련 애니메이션 지속시간을 `0.01ms`로. **`animation: none`을 쓰지 않는다** — `animationend`가 발생하지 않아 언마운트가 막히고, 고치려는 굳음 버그가 그대로 재도입된다(design.md Decision 3)

## 3. 곡 추가·편집 화면 (front/)

- [x] 3.1 `app/routes/PlaylistWriteView.tsx:266` — **버그 직접 수정**. 곡 편집 `Modal`의 `onClose={() => {}}`를 `() => setIsOpenEditTrack(false)`로 교체
- [x] 3.2 `app/routes/PlaylistWriteView.tsx:218` — 곡 추가 '+'를 `onClick` 달린 `<div>`에서 `<button type="button">`으로 교체. 기존 시각(`text-6xl` 중앙 정렬, hover 효과)을 유지하고 포커스 링을 추가. **포커스 복원의 전제**이자 키보드로 곡을 추가할 수 있게 하는 변경이다(design.md Decision 7)
- [x] 3.3 `app/routes/PlaylistWriteView.tsx:258` — 뒤로가기 모달의 `<h2>뒤로가기</h2>`를 `ModalTitle`로 교체
- [x] 3.4 뒤로가기 모달의 초기 포커스를 **취소**(비파괴) 버튼(`PlaylistWriteView.tsx:262`)에 고정 — 그 버튼에 ref를 달아 `<Modal initialFocusRef={...}>`로 전달한다. 지정하지 않으면 트리 순서상 앞선 파괴적 '확인'(`:261`)에 포커스가 간다(design.md Decision 6). `Button`(`app/components/ui/Button.tsx`)의 `ButtonProps`는 `ref`를 선언하지 않아 타입 오류가 나므로 `ref?: Ref<HTMLButtonElement>`를 prop 타입에 추가한다(React 19에서는 `{...rest}` 스프레드로 DOM까지 그대로 전달된다)
- [x] 3.5 `app/components/ui/TrackEditLayer.tsx:153` — `<h2>`를 `ModalTitle`로 교체하되 동적 제목(`isEditing() ? "곡 편집" : "곡 추가"`)과 대표곡 별 아이콘 배치를 유지
- [x] 3.6 `app/components/ui/TrackEditLayer.tsx:171-175` — YouTube URL `<input>`에 ref를 달고, 그 ref를 `PlaylistWriteView`의 곡 편집 `<Modal initialFocusRef={...}>`로 전달해 초기 포커스를 고정한다. ref 소유자는 `Modal`을 렌더하는 `PlaylistWriteView`이므로 `TrackEditLayer`가 prop으로 ref를 받아 input에 붙인다. **URL이 이미 있는 편집 진입에서는 `<YouTube>` iframe(`:161`)이 트리 순서상 앞서** 포커스가 교차 출처 iframe으로 들어가므로 이 고정이 필수다(design.md Decision 6)

## 4. 나머지 모달 소비자 (front/)

- [x] 4.1 `app/components/ui/PasswordModal.tsx:32` — `<h3>`를 `ModalTitle`로 교체. 비밀번호 `<input>`(`:39`)의 기존 `autoFocus`를 **제거하고** ref + `<Modal initialFocusRef={...}>`로 이관한다(현재는 우연히 첫 포커스 가능 자손이라 동작하는 것이므로 마크업이 바뀌면 조용히 어긋난다, design.md Decision 6)
- [x] 4.2 `app/components/ui/PasswordModal.tsx:22,30` — `handleClose`의 `if (!isLoading)` 가드를 **제거**하고(핸들러는 `setPassword("")` + `onClose()`를 무조건 수행) 닫기 억제를 `<Modal dismissible={!isLoading} ...>`로 이관. ESC 억제는 최선 노력이며 플랫폼 강제 닫힘은 막을 수 없음을 주석으로 남긴다(design.md Decision 5a)
- [x] 4.3 `app/components/ui/RoomCreate.tsx:150` — 헤딩이 아닌 `<div className="text-xl font-bold mb-6 ...">방 만들기</div>`를 `ModalTitle`로 교체
- [x] 4.4 `Modal` 호출부 전수 확인 — `PlaylistWriteView`(2곳)·`RoomCreate`·`PasswordModal` 네 곳 모두가 새 `onClose` 의미("**무조건 이행**하는 닫기 신호")에 맞게 `isOpen`을 내리는지, 닫기 억제가 필요한 곳은 `dismissible`을 쓰는지 점검. 저장소에 `onClose={() => {}}`류의 no-op 거부 idiom이 남지 않았는지 grep으로 확인한다. **TypeScript 시그니처가 그대로라 컴파일러가 잡아주지 않으므로 눈으로 확인한다**
- [x] 4.5 `app/routes/RoomsView.tsx:131` — '방 만들기' 카드를 `onClick` 달린 `<div>`에서 `<button type="button">`으로 교체. 기존 시각(점선 테두리·hover 효과·`min-h-[160px]`)을 유지하고 포커스 링을 추가(design.md Decision 7)
- [x] 4.6 `app/routes/RoomsView.tsx:144` — 방 카드를 `<button type="button">`으로 교체. 자식이 `div`/`img`/`svg`뿐이라 중첩 인터랙티브 요소가 없다. 연결 중(`connectingRoomId === room.id`)의 기존 `pointer-events-none` 비활성화를 `disabled`로도 반영해 키보드 조작에서도 막히게 한다. **비밀번호 모달의 유일한 진입 경로**라 이 치환 없이는 그 모달을 키보드로 열 수 없다(design.md Decision 7)
- [x] 4.7 `app/components/ui/RoomCreate.tsx` — 폼 state(`roomName`·`selectedRoomCapacity`·`usePassword`·`password`·`selectedPlaylist`·`searchTerm`·`playlistTab`)를 `isOpen`이 `false→true`로 바뀔 때 초기화. `RoomCreate`는 `RoomsView`에 **항상 마운트**된 채 이 state를 들고 있으므로(`RoomsView.tsx:196-208`), 완전히 닫힌 뒤의 평범한 재오픈에서도 직전 입력이 그대로 남는다. `Modal`보다 **위**의 state라 `Modal`의 children 언마운트·내부 키로는 리셋되지 않는다(design.md Decision 4의 책임 경계 표)
- [x] 4.8 `app/routes/RoomsView.tsx:77-92` — `handlePasswordSubmit`에 **인-플라이트 세대 가드** 추가. 요청마다 증가하는 세대 번호(또는 ref)를 두고, `connectToRoom` 결과가 돌아왔을 때 세대가 어긋나면(= 그 사이 모달이 닫혔으면) `setConnection`·`navigate`를 실행하지 않고 `client.deactivate()`로 연결을 정리한다. `handlePasswordClose`(`:94-97`)에서 세대를 무효화한다. **강제 닫힘 경로가 실재하므로 필요하다** — 없으면 사용자가 빠져나온 방으로 몇 초 뒤 튕겨 들어간다(design.md Decision 5a)
- [x] 4.9 `app/components/ui/RoomCreate.tsx:148` — `<Modal isOpen={isOpen} onClose={onClose}>`에 **`dismissible={!isCreating}`** 추가. 생성 중 배경 클릭은 확실히 억제되고, ESC 억제는 최선 노력이다(design.md Decision 5b)
- [x] 4.10 `app/components/ui/RoomCreate.tsx:307-325` — '만들기' 핸들러에 **인-플라이트 세대 가드** 추가. 요청 시점의 세대(또는 ref)를 `isOpen` 변화로 무효화하고, `createRoom` 성공이 돌아왔을 때 세대가 어긋나면(= 그 사이 모달이 닫혔으면) **`onCreated`를 호출하지 않는다**(자동 입장 억제). 방은 이미 서버에 생성돼 되돌릴 수 없으므로, 이 경로에서는 부모에게 **방 목록 갱신 신호**를 올려 `RoomsView`가 `fetchRooms()`를 다시 호출하게 한다(`RoomsView.tsx:31-42`의 초기 로드를 재사용 가능한 함수로 추출). 신호 수단(전용 콜백 prop 등)은 구현 시 정한다(design.md Decision 5b)

## 5. 전역 토스트 top layer 전환 (front/)

- [x] 5.1 `app/components/ui/Toast.tsx` — sonner `<SonnerToaster>`를 `popover="manual"` 컨테이너로 감싸고 마운트 시 `showPopover()`로 top layer에 올린다. **`typeof el.showPopover === "function"` feature detection으로 반드시 감쌀 것** — 미지원 브라우저에서는 승격을 건너뛰고 종전 일반 레이어 렌더로 폴백한다. `<Toaster />`는 앱 셸에 상시 렌더되므로 가드 없이 호출하면 마운트 이펙트의 예외가 화면 전체를 날린다(design.md Decision 8 '지원 기준선과 미지원 폴백'). `<dialog>`가 top layer로 승격되면 일반 레이어의 토스트는 `z-index`와 무관하게 `::backdrop` 아래로 내려가기 때문이다(design.md Decision 8)
- [x] 5.2 `Toast.tsx` — popover 기본 UA 스타일(`inset: auto`, `border`·`padding`·`margin`, `overflow`)을 초기화해 기존 위치(`position="bottom-center"`)와 크기를 유지하고, 컨테이너는 `pointer-events: none`(개별 토스트만 `auto`)으로 두어 배경 조작을 막지 않게 한다
- [x] 5.3 `Toast.tsx` — `Modal`의 재승격 신호(1.13)를 구독해 `hidePopover()` → `showPopover()`로 **재승격**한다. top layer는 승격 순서로 쌓이므로 재승격 없이는 나중에 열린 모달 아래에 계속 깔린다. 모달이 연속으로 열려도 마지막 모달 위에 오도록 매 신호마다 수행한다. 5.1과 동일한 feature detection 가드를 적용해, 미지원 브라우저에서는 재승격도 건너뛴다
- [x] 5.4 `app/root.tsx:38`의 `<Toaster />` 렌더 위치는 그대로 둔다(top layer 승격은 5.1이 담당하므로 `root.tsx` 자체는 변경 없음). 전환 후 일반 화면 회귀는 7.12(d)에서 확인한다

## 6. 검증 — 자동 (front/)

- [x] 6.1 `npm run typecheck` 통과
- [x] 6.2 `npm run build` 통과

## 7. 검증 — 수동 (front/)

- [ ] 7.1 **주 버그 재현 확인** — 새 플레이리스트 화면에서 '+' → 곡 추가 레이어 → 바깥 클릭으로 닫기 → '+' 다시 클릭 → 레이어가 정상적으로 열린다
- [ ] 7.2 ESC 동작 — 네 모달 모두 ESC로 닫히고, 닫은 뒤 다시 열린다. 단 비밀번호 모달은 연결 중(`isLoading`)에 배경 클릭으로 닫히지 않고 모달이 **사라지지도 않는다**(잠복 결함 해소 확인)
- [ ] 7.2a **강제 닫힘 동기화 확인**(design.md Decision 2 함정 4) — (a) 모달을 연 뒤 5초 이상 아무 조작 없이 두고 ESC를 한 번 누른다, (b) 연결 중 비밀번호 모달에서 ESC를 중간 조작 없이 **연속 두 번** 누른다. 두 경우 모두 모달이 사라졌다면 **같은 트리거로 다시 열리는지** 확인한다(열리지 않으면 굳음 버그 재도입). Chrome·Firefox·Safari에서 각각 확인
- [ ] 7.2b **늦게 도착한 연결 성공 무시 확인**(4.8) — 비밀번호 모달이 연결 대기 중 강제로 닫힌 뒤(7.2a의 (b) 경로) 방 화면으로 자동 이동하지 않는지 확인. 네트워크 스로틀링으로 연결 지연을 유도해 재현한다
- [ ] 7.2c **방 생성 중 닫힘 확인**(4.9·4.10) — 네트워크 스로틀링 상태에서 방 만들기 모달의 '만들기'를 누른 직후 (a) 배경 클릭이 억제되는지, (b) ESC로 모달이 닫힌 경우 생성이 성공해도 **방으로 자동 입장하지 않는지**, (c) 그 뒤 방 목록에 방금 만들어진 방이 보이고 눌러서 직접 입장할 수 있는지 확인
- [ ] 7.3 재오픈 시 children 리셋(1.9) — 곡 A 편집 레이어를 ESC로 닫아 **완전히 사라진 뒤** 곡 목록에서 곡 B를 클릭해, 곡 B의 정보(URL·제목·재생 구간·반복 횟수·추가 정답)가 표시되고 곡 A의 입력이 남지 않는지 확인
- [ ] 7.4 키보드 전용 조작 — Tab만으로 '+'에 도달해 Enter로 열고, Tab이 레이어 안에서만 순환하는지, ESC로 닫았을 때 포커스가 '+'로 돌아오는지 확인
- [ ] 7.4a **초기 포커스 확인**(1.12·3.4·3.6·4.1) — (a) 뒤로가기 확인 모달을 열었을 때 초기 포커스가 **취소** 버튼인지(열자마자 Enter를 눌러 편집 내용이 버려지지 않는지), (b) URL이 이미 있는 **기존 곡을 클릭해** 편집 레이어를 열었을 때(=YouTube iframe이 렌더된 상태) 초기 포커스가 iframe이 아니라 URL 입력인지, (c) 비밀번호 모달의 초기 포커스가 비밀번호 입력인지 확인
- [ ] 7.5 배경 스크롤 잠금 — 모달이 열린 동안 배경이 스크롤되지 않고, 모달 내용이 화면보다 길면 모달 내부는 스크롤되는지 확인(곡 추가 레이어를 좁은 창에서)
- [ ] 7.6 동작 축소 설정 — OS/브라우저의 "동작 줄이기"를 켠 상태에서 모달을 열고 닫아 **굳지 않는지** 확인(design.md Decision 3의 재발 경로)
- [ ] 7.7 top-layer 시각 확인 — 모달 안의 `absolute z-10` 요소가 정상 표시되는지 확인: 곡 추가 레이어의 반복 횟수 드롭다운(`Dropdown.tsx:50`), 방 만들기의 플레이리스트 자동완성 목록(`RoomCreate.tsx:208`)
- [ ] 7.8 YouTube iframe 공존 확인 — 곡 추가 레이어에서 URL을 입력해 미리보기가 뜬 상태로 Tab 순환·ESC 닫기가 정상 동작하는지 확인(iframe이 포커스 트랩에 미치는 영향, design.md Decision 2)
- [ ] 7.9 모바일 뷰 확인 — 좁은 화면에서 `<dialog>`가 화면을 벗어나지 않고 기존 레이아웃(`mx-4`·`max-h-[90vh]`)과 동일하게 보이는지 확인
- [ ] 7.10 방 목록 키보드 전용 조작 — Tab만으로 '방 만들기' 카드와 (비밀번호 걸린) 방 카드에 도달해 Enter로 각각 모달을 열고, ESC로 닫았을 때 포커스가 눌렀던 카드로 돌아오는지 확인(design.md Decision 7)
- [ ] 7.11 방 만들기 모달 재오픈 리셋 — 방 이름·플레이리스트를 입력하다 배경 클릭으로 닫고 **모달이 완전히 사라진 뒤** '방 만들기' 카드를 다시 눌러, 이전 입력이 남지 않는지 확인(4.7의 소비자 측 리셋 검증)
- [ ] 7.12 **모달 위 토스트 가시성 확인**(5절, design.md Decision 8) — (a) 모달이 열린 상태에서 토스트가 `::backdrop` 위에 어두워지지 않고 선명하게 보이는지, (b) 토스트 컨테이너가 모달 조작(버튼 클릭·입력)을 가로막지 않는지, (c) **`RoomCreate` 생성 실패 시 실패 토스트가 실제로 보이는지**(서버 오류를 유도하거나 네트워크를 끊어 재현 — 이 경로는 모달이 열린 채 토스트만 뜬다, `RoomCreate.tsx:320`), (d) 모달이 없는 일반 화면에서 토스트 위치(`bottom-center`)·자동 소멸이 회귀하지 않았는지 확인
