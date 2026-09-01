## Why

새 플레이리스트 화면에서 **곡 추가 레이어 바깥을 한 번 누르면, 그 뒤로 '+'를 눌러도 레이어가 다시 열리지 않는다.** 페이지를 벗어나기 전까지 곡을 추가할 수 없으므로 화면이 사실상 잠긴다.

원인은 `Modal`이 **열림 상태를 부모와 이중으로 소유**하는 데 있다. 부모는 `isOpen` prop을, `Modal`은 자기 내부 `visible`/`closing`(`Modal.tsx:10-11`)을 각각 들고 있다. 배경 클릭 핸들러(`Modal.tsx:39` → `handleClose`)는 **내부 사본만** 바꾸고 부모에게는 알리지 않으며, 부모에게 알리는 `onClose()`는 퇴장 애니메이션이 끝난 뒤에야 호출된다(`Modal.tsx:30`).

```
parent isOpen :  false ──[+]──▶ true ─────────────────────────▶ true  ⚠️ 그대로
                                 │
Modal visible :  false ───────▶ true ──[배경클릭]──▶ closing ──▶ false
                                                        │
                                                  animationend
                                                        │
                                                        ▼
                                          onClose = () => {}  (PlaylistWriteView.tsx:266)

다시 [+] → setIsOpenEditTrack(true) → 이미 true → 상태 변화 없음
        → useEffect deps [isOpen] 안 바뀜 → visible 영원히 false
```

`isOpen`이 `true`로 굳고, `true → true`는 리렌더도 이펙트도 트리거하지 않아 복구 경로가 없다.

**같은 뿌리의 잠복 결함이 하나 더 있다.** `PasswordModal.tsx:22`의 `handleClose`는 `isLoading`이면 `onClose()`를 호출하지 않는다. 그러나 `Modal`은 이미 스스로 닫힌 뒤이므로, 방 입장 연결 중(최대 5초, `stomp.ts:3`) 배경을 누르면 모달이 사라지고 `passwordModal.isOpen`은 `true`로 굳는다 — 곡 추가 레이어와 완전히 같은 증상이다. 아직 사용자 신고가 없을 뿐 코드는 같은 함정을 밟고 있다.

더 근본적으로 `onClose`는 **두 가지 의미를 겸직**한다: ① "사용자가 닫으려 한다"(의도 — 부모가 승인/거부해야 함)와 ② "퇴장 애니메이션이 끝났다"(통보 — 부모는 정리만). `Modal`은 ②의 타이밍에 ①의 이름으로 호출하고, 부모는 둘을 구분할 수단이 없다. 그래서 "배경 클릭으로 닫히지 않게 하고 싶다"는 요구가 생겼을 때 부모가 할 수 있는 최선이 콜백을 `() => {}`로 무력화하는 것뿐이었고 — `Modal`은 이미 닫힌 뒤라 아무 효과가 없었다. 지금 버그는 이 겸직 구조의 직접적 산물이다.

여기에 더해 현재 `Modal`에는 **접근성 장치가 전혀 없다**: ESC로 닫을 수 없고, 포커스가 모달 밖으로 새어 나가며(Tab 트랩 없음), 배경 요소가 여전히 포커스 가능하고, 닫은 뒤 포커스가 트리거로 돌아오지 않으며, `role="dialog"`도 접근 가능한 이름도 없다. 키보드·스크린리더 사용자에게 이 모달들은 사용할 수 없는 UI다.

본 변경은 세 가지를 한 번에 처리한다: **(1) 열림 상태의 단일 소유권 확립(controlled 전환)**, **(2) 그로써 두 곳의 굳음 결함 제거**, **(3) 네이티브 `<dialog>` 채택으로 접근성 확보**.

이 설계를 끌고 가는 비자명한 결정들(각각 `design.md`에서 상술):

- **`isOpen`을 유일한 진실로 삼고, `Modal`은 스스로 닫기로 결정하지 않는다.** 배경 클릭·ESC는 `onClose()` 호출로만 전달되고 실제 닫힘은 부모가 `isOpen`을 `false`로 내릴 때 일어난다. 내부 상태는 "언제 DOM에서 떼어낼지"만 담당하는 파생 상태로 강등된다. **닫기 거부는 `onClose`를 no-op으로 두는 방식이 아니라 새 `dismissible` prop으로 선언한다** — `onClose`는 부모가 무조건 이행해야 하는 신호로 의미가 확정된다(Decision 1).
- **커스텀 `div` 오버레이 대신 네이티브 `<dialog>` + `showModal()`을 쓴다.** 포커스 트랩·배경 `inert`·닫을 때 포커스 복원·top-layer 승격을 브라우저가 제공한다. 특히 곡 추가 레이어 안에는 **YouTube iframe**이 있어 직접 짠 포커스 트랩으로 다루기 까다롭다(Decision 2).
- **네이티브 `close` 이벤트를 권위 있는 신호로 삼아 강제 닫힘을 수락한다.** `<dialog>`의 ESC 처리에는 "사용자를 가두지 못하게" 하는 남용 방지 규칙이 있어, `cancel`을 `preventDefault()`로 막아도 **활성화 만료 후 첫 ESC**나 **연속 두 번째 ESC**에서는 브라우저가 `cancel` 없이 대화 상자를 닫는다. 이를 다루지 않으면 `isOpen`만 `true`로 남아 **고치려는 굳음 버그가 네 모달 전부에서 재도입된다.** 그래서 `close` 이벤트에서 `onClose()`를 호출해 부모 상태를 동기화하고, 닫기 억제(`dismissible={false}`)는 **최선 노력**임을 계약에 명시한다(Decision 2 함정 4).
- **퇴장 애니메이션 후 언마운트를 `animationend` 이벤트에만 의존하지 않는다.** 접근성 작업으로 `prefers-reduced-motion` 대응을 넣을 때 흔한 방식(`animation: none`)을 쓰면 **`animationend`가 영영 오지 않아 지금과 똑같이 모달이 굳는다.** 지속시간 0 처리 + 타임아웃 폴백으로 이 재발 경로를 원천 차단한다(Decision 3).
- **곡 추가 레이어는 배경 클릭·ESC로 닫힌다(작성 중이던 입력은 버려진다).** 더티 상태 확인 프롬프트는 모달 위 모달이 되어 범위를 넘으므로 후속 과제로 둔다(Decision 4).
- **비밀번호 모달의 연결 중 닫기 억제는 `dismissible={!isLoading}`로 선언하되 최선 노력이다.** 배경 클릭은 확실히 억제되지만 ESC는 플랫폼이 강제로 닫을 수 있고, 이 모달은 대기 중 모든 컨트롤이 `disabled`라 그 조건에 특히 잘 걸린다. 그래서 **늦게 도착한 연결 성공을 무시하는 인-플라이트 가드(`RoomsView`의 세대 토큰 + `client.deactivate()`)를 이번 범위에 포함한다** — 없으면 사용자가 빠져나온 방으로 몇 초 뒤 튕겨 들어간다(Decision 5a).
- **방 만들기 모달도 같은 규칙을 받는다 — `dismissible={!isCreating}` + in-flight 가드.** `createRoom`이 도는 중 모달이 닫히면 늦게 도착한 성공이 `onCreated` → `navigate`로 **취소한 방에 사용자를 밀어 넣는다.** 다만 방은 이미 서버에 만들어져 되돌릴 수 없으므로, 가드는 **자동 입장만** 억제하고 방 목록을 갱신해 사용자가 스스로 들어갈 경로를 남긴다(Decision 5b).
- **전역 토스트도 top layer로 올린다(모달보다 나중에 승격).** `showModal()`은 `<dialog>`와 `::backdrop`을 top layer로 올려, 일반 레이어의 sonner `<Toaster />`(`root.tsx:38`)를 `z-index`와 무관하게 배경 아래로 밀어낸다. 특히 **방 생성 실패**는 모달을 연 채 토스트만 띄우므로(`RoomCreate.tsx:320`) 유일한 실패 피드백이 사라진다. Toaster를 popover로 top layer에 올리고, top layer가 승격 **순서**로 쌓이므로 모달이 열릴 때마다 재승격한다(Decision 8).
- **초기 포커스는 React `autoFocus`가 아니라 `initialFocusRef`로 고정한다.** React는 `autoFocus` prop을 DOM 속성으로 내보내지 않고 commit 시 `focus()` 폴리필로 대체하는데, 그 시점의 `<dialog>`는 `showModal()` 전이라 `display:none`이어서 무효다. 그대로 두면 뒤로가기 확인 모달은 **파괴적인 '확인' 버튼**에, 곡 편집 레이어는 **YouTube iframe**에 초기 포커스가 간다. `Modal`이 `showModal()` 직후 명령형으로 `focus()`를 준다(Decision 2 함정 3·Decision 6).
- **접근 가능한 이름은 `ModalTitle` 컴포넌트 + `useId`로 붙인다.** 곡 추가 레이어의 제목은 `곡 추가`/`곡 편집`으로 동적이라 문자열 prop을 쓰면 표시 제목과 접근 이름이 어긋날 수 있다. 덤으로 세 소비자가 제각각 하드코딩한 제목 마크업(`<h2>`·`<h3>`·`<div>`)이 하나로 정리된다(Decision 6).
- **모달을 여는 트리거 중 중첩 없는 3곳을 `<button>`으로 바꾼다** — 곡 목록 '+'(`PlaylistWriteView.tsx:218`), '방 만들기' 카드(`RoomsView.tsx:131`), 방 카드(`RoomsView.tsx:144`). 지금은 모두 `onClick`만 달린 `<div>`라 Tab으로 도달할 수도 Enter로 누를 수도 없다. "닫을 때 트리거로 포커스 복원"은 **트리거가 포커스 가능해야 성립**하므로 곁가지가 아니라 접근성 요구의 일부다. 특히 방 카드는 비밀번호 모달의 **유일한 진입 경로**라 빠뜨리면 그 모달을 키보드로 열 수 없다. 반면 곡 목록 행(중첩 클릭 요소)과 데스크톱 뒤로가기(`NavigationItem` — `<Link>` 중첩)는 구조 재작업이 필요해 범위 밖이다(Decision 7).

## What Changes

### front/ — `Modal` 재작성 (`app/components/ui/Modal.tsx`)

- **API 변경**: `isOpen`(부모 소유, 유일한 진실) · `onClose`(닫기 신호, **부모가 무조건 이행**) · `dismissible?: boolean`(기본 `true`, 닫기 억제 정책 — 신규) · `initialFocusRef?: RefObject<HTMLElement | null>`(`showModal()` 직후 명령형 `focus()` 대상 — 신규) · `children`. `onClose`에서 "애니메이션 종료 통보" 의미를 **제거**한다 — 언마운트는 `Modal` 내부 관심사가 된다
- 커스텀 `div` 오버레이 → **네이티브 `<dialog>` + `showModal()`**. `::backdrop`에 기존 배경 스타일(`bg-black/60 backdrop-blur-sm`) 이식, `<dialog>` 자체는 패딩·배경 없이 두고 안쪽 래퍼가 시각(`bg-surface`·`rounded-2xl`·`shadow-glow-cyan`·`max-h-[90vh]`)을 담당 — 그래야 배경 클릭 판정(`e.target === dialogRef.current`)이 정확하다
- 내부 상태를 `visible`/`closing` 두 boolean에서 **`phase: "closed" | "open" | "closing"`** 단일 상태로 정리. `phase`는 열림 여부를 **결정하지 않고** 지연 언마운트만 담당
- ESC: `<dialog>`의 `cancel` 이벤트를 `preventDefault()`로 가로채, `dismissible`이면 `onClose()`로 라우팅. `dismissible={false}`면 억제하되 **최선 노력**이다
- 배경 클릭: `dismissible`이면 `onClose()`로 라우팅, 아니면 무시(동작은 ESC와 동일하되 억제가 확실하다)
- **네이티브 `close` 이벤트 구독(신규)**: 브라우저가 강제로 닫은 경우에도 `phase`를 내리고 `onClose()`를 호출해 부모 `isOpen`을 동기화. 부모 주도 닫힘 경로와 중복 호출되지 않도록 가드
- 언마운트: `animationend`(`e.currentTarget === e.target` 가드 유지) **또는** 타임아웃 중 먼저 오는 쪽. `showModal()`은 StrictMode 이중 이펙트에 대비해 `if (!dialog.open)` 가드
- 배경 스크롤 잠금: 모듈 수준 참조 카운터로 `document.body`의 `overflow` 제어(중첩·동시 오픈 대비)
- 초기 포커스: `showModal()` **직후** `initialFocusRef`에 명령형 `focus()`. React `autoFocus` prop은 `<dialog>`에서 무효라 쓰지 않는다
- 토스트 재승격 신호(신규): `showModal()` 직후 전역 토스트 컨테이너에 재승격을 알린다(top layer는 승격 순서로 쌓인다)
- 신규 `ModalTitle` — `Modal`이 `useId()`로 만든 id를 컨텍스트로 내려주고 `<h2 id>`로 렌더, `<dialog aria-labelledby>`가 이를 가리킨다

### front/ — 소비자·트리거 수정

- `app/routes/PlaylistWriteView.tsx`
  - **버그 직접 수정**: 곡 편집 `Modal`의 `onClose={() => {}}`(266행)를 `() => setIsOpenEditTrack(false)`로 교체
  - `<h2>뒤로가기</h2>`(258행)를 `ModalTitle`로 교체. 뒤로가기 모달의 초기 포커스를 **취소**(262행, 비파괴) 버튼으로 고정(`initialFocusRef`) — 그러지 않으면 트리 순서상 앞선 파괴적 '확인'(261행)이 잡힌다. 이를 위해 `Button`의 prop 타입에 `ref`를 추가한다
  - 곡 추가 '+'(218행)를 `<div>` → `<button type="button">`로 교체(포커스 복원 대상)
- `app/components/ui/TrackEditLayer.tsx`: `<h2>`(153행) → `ModalTitle`(동적 `곡 추가`/`곡 편집` 유지). YouTube URL 입력(171-175행)에 ref를 달아 `Modal`의 `initialFocusRef`로 전달(URL이 이미 있으면 iframe이 트리 순서상 앞서므로 필수)
- `app/components/ui/PasswordModal.tsx`: `<h3>`(32행) → `ModalTitle`. 비밀번호 입력(39행)의 `autoFocus`를 `initialFocusRef`로 이관. `handleClose`(22행)의 `isLoading` 가드를 걷어내고 닫기 억제를 `<Modal dismissible={!isLoading}>`로 이관
- `app/components/ui/RoomCreate.tsx`: 제목 `<div>`(150행 부근) → `ModalTitle`. 폼 state가 `Modal`보다 위에 있어 내부 키로 리셋되지 않으므로 `isOpen`이 `false→true`일 때 **소비자가 직접 초기화**. 생성 중 닫기 억제를 `<Modal dismissible={!isCreating}>`로 선언하고, '만들기' 핸들러에 **인-플라이트 세대 가드**를 둬 모달이 닫힌 뒤 도착한 `createRoom` 성공이 `onCreated`(→ 자동 입장)로 이어지지 않게 한다. 방은 이미 생성됐으므로 그 경우 부모에 **방 목록 갱신 신호**를 올린다
- `app/routes/RoomsView.tsx`: '방 만들기' 카드(131행)와 방 카드(144행)의 `<div onClick>` → `<button type="button">`(포커스 복원 대상이자 비밀번호 모달의 유일한 진입 경로). 방 카드는 연결 중 비활성화를 `disabled`로도 반영. `handlePasswordSubmit`(77-92행)에 **인-플라이트 세대 가드** 추가 — 모달이 닫힌 뒤 도착한 연결 성공은 `setConnection`·`navigate`를 실행하지 않고 `client.deactivate()`로 정리. 초기 방 목록 로드(31-42행)를 재사용 가능한 함수로 추출해 `RoomCreate`의 목록 갱신 신호에 응답

### front/ — 전역 토스트 top layer 전환 (`app/components/ui/Toast.tsx`)

- sonner `<Toaster />`(`root.tsx:38`에서 렌더)를 `popover="manual"` 컨테이너로 감싸고 마운트 시 `showPopover()`로 top layer에 올린다. `<dialog>`가 top layer로 승격되면 일반 레이어의 토스트는 `z-index`와 무관하게 `::backdrop` 아래로 내려가기 때문이다
- `Modal`의 재승격 신호를 받아 `hidePopover()` → `showPopover()`로 **재승격**한다(top layer는 승격 순서로 쌓이므로 한 번 올리는 것만으로는 나중에 열린 모달에 가려진다)
- popover 기본 UA 스타일(`inset: auto`·`border`·`padding`·`margin`)을 초기화해 기존 위치(`bottom-center`)를 유지하고, 컨테이너는 `pointer-events: none`(개별 토스트만 `auto`)으로 둔다

### front/ — 애니메이션 (`app/app.css`)

- `prefers-reduced-motion: reduce`에서 모달 관련 애니메이션 지속시간을 0에 가깝게(`0.01ms`) 만든다. **`animation: none`은 쓰지 않는다** — `animationend`가 발생하지 않아 언마운트가 막힌다
- `::backdrop`용 페이드 인/아웃 유틸 추가(기존 `fade-in`/`fade-out` 키프레임 재사용)

### back/ · infra/ — 변경 없음

프론트 전용 변경이다. API 계약·DB·Redis·Kafka 영향 없음.

## Capabilities

### New Capabilities

- `modal-dialog`: 앱 전역 모달 다이얼로그 프리미티브의 행위 — 열림 상태의 단일 소유권(부모 `isOpen`), 닫기 **요청**과 실제 닫힘의 분리, 퇴장 애니메이션 후 지연 언마운트와 그 실패 안전장치, 재오픈 시 상태 리셋, 키보드 접근성(ESC·포커스 트랩·포커스 복원), 배경 비활성화·스크롤 잠금, 접근 가능한 이름. 특정 도메인(playlist/room)에 속하지 않는 **UI 프리미티브 관심사**라 별도 capability로 둔다.

### Modified Capabilities

- 없음. 기존 spec 중 `Modal`의 행위를 규정한 것이 없다(`room-round-ui`는 라운드 화면 전용 오버레이를 다루며 `Modal`을 쓰지 않는다).

## Impact

- **서브프로젝트**: `front/`만. `back/`·`infra/` 영향 없음
- **헥사고날 계층**: 해당 없음(프론트 전용)
- **DB 스키마 / ES 매핑 / Kafka 토픽 / Redis 키**: 영향 없음
- **API 계약 변화**: 없음
- **외부 의존성**: 신규 npm 패키지 없음. 이미 있는 `framer-motion`도 쓰지 않는다(기존 CSS 키프레임 유지)
- **영향 받는 화면**: `Modal`을 쓰는 전부 —
  - `/playlists/create`·`/playlists/:id/modify` (곡 추가·편집 레이어, 뒤로가기 확인)
  - `/` (방 만들기, 비밀번호 입력)
  - **전역**: 토스트(`Toast.tsx`, `root.tsx`)가 top layer로 옮겨가므로 토스트를 쓰는 모든 화면이 시각적 회귀 확인 대상이다(Decision 8)
- **동작 변화**:
  - 곡 추가 레이어 바깥을 눌러도 화면이 잠기지 않고 '+'로 다시 열린다 **(주 수정)**
  - 곡 추가 레이어가 배경 클릭·ESC로 닫히며, 이때 작성 중이던 입력은 버려진다
  - 방 입장 연결 중 배경 클릭이 모달을 없애버리지 않는다(요청이 억제되고 모달은 그대로 유지)
  - 브라우저가 억제를 무시하고 모달을 강제로 닫더라도 부모 상태가 함께 내려가, 같은 트리거로 다시 열린다. 연결 대기 중 그렇게 닫힌 경우 늦게 도착한 성공이 사용자를 방으로 끌고 가지 않는다
  - 방 생성 중에는 배경 클릭이 억제되고, 그럼에도 모달이 닫힌 경우 생성이 성공해도 **자동 입장하지 않는다.** 만들어진 방은 서버에 남으므로 방 목록이 갱신되어 사용자가 직접 입장할 수 있다
  - 모든 모달이 ESC로 닫히고, Tab이 모달 안에 갇히며, 닫으면 포커스가 트리거로 돌아온다
  - 모달이 열린 동안 배경이 스크롤되지 않는다
  - 곡 목록 '+'·'방 만들기' 카드·방 카드를 키보드로 조작할 수 있다(방 입장·비밀번호 모달 열기가 키보드만으로 가능해진다)
  - 방 만들기 모달을 닫는 중 다시 열어도 이전 입력이 남지 않는다
  - 모달이 열려 있어도 토스트가 모달 위에 보인다 — 특히 **방 생성 실패 토스트**(모달을 연 채 뜨는 유일한 실패 피드백)가 가려지지 않는다. 단 모달이 열린 동안 배경이 inert가 되므로 그 토스트를 클릭해 닫을 수는 없다(자동 소멸 3초)
  - 모달을 열면 초기 포커스가 결정론적으로 지정된 요소로 간다 — 뒤로가기 확인 모달은 **취소** 버튼(파괴적 '확인'이 아님), 곡 편집 레이어는 URL 입력(YouTube iframe이 아님)
- **컴포넌트 API 파괴적 변경**: `Modal`의 `onClose` 의미가 "애니메이션 종료 통보"에서 "**부모가 무조건 이행해야 하는 닫기 신호**"로 바뀌고, 닫기 억제는 새 `dismissible` prop으로 옮겨간다(`onClose`를 no-op으로 두는 거부 idiom은 폐기). 소비자 4곳(`PlaylistWriteView` 2곳, `RoomCreate`, `PasswordModal`)을 모두 이 변경에 맞춰 수정하므로 저장소 안에 미이행 호출부는 남지 않는다
- **테스트**: 프론트에 테스트 프레임워크가 없어 자동 검증은 `npm run typecheck` + `npm run build`이고, 행위 검증은 수동 시나리오로 수행한다(`tasks.md` 7절)
- **롤백**: 프론트 단일 PR `git revert`. 서버·인프라 변경이 없어 코드 원복으로 완전 롤백
- **알려진 트레이드오프/리스크**(design에서 상술):
  - 곡 추가 레이어가 배경 클릭·ESC로 닫히면 **작성 중 입력이 유실**된다 — 더티 확인 프롬프트는 후속 과제
  - `<dialog>`는 top-layer로 승격된다. 모달 내부에는 `absolute z-10`을 쓰는 요소가 있으나(`Dropdown.tsx:50` — 곡 추가의 반복 횟수 드롭다운, `RoomCreate.tsx:208` — 플레이리스트 자동완성 목록) 이들은 모달 내부 스택 컨텍스트 안이라 top-layer 승격의 영향을 받지 않는다. 다만 시각 확인 대상으로 남긴다
  - 반대로 모달 **밖**에서 모달 위에 떠야 하는 전역 토스트는 영향을 받는다 — Toaster를 popover로 top layer에 올리고 모달이 열릴 때마다 재승격해 해결한다. 승격 순서에 의존하는 구조라 새 전역 오버레이가 생기면 같은 처리를 해야 한다(Decision 8)
  - `<dialog>` 초기 포커스는 React `autoFocus` prop으로 고정되지 않는다(React가 속성을 내보내지 않고 commit 시 `focus()` 폴리필로 대체하는데, 그 시점엔 다이얼로그가 아직 `display:none`이다). `Modal`이 `showModal()` 직후 `initialFocusRef`에 명령형 `focus()`를 주는 방식으로 고정한다
- **범위 외(후속 과제)**:
  - 곡 편집 레이어의 **더티 상태 확인 프롬프트**("작성 중인 내용을 버릴까요?")
  - 비밀번호 모달의 명시적 **'연결 취소' UI** — 연결 중에는 `dismissible={false}`로 닫기를 억제하고, 취소 UX·에러 문구·재시도 흐름 정의는 후속. (강제 닫힘에 대비한 인-플라이트 가드 자체는 **이번 범위 안**이다 — Decision 5a)
  - 방 만들기 모달을 닫았을 때 **이미 생성된 방을 서버에서 되돌리는 것**(삭제·생성 취소 API) — 삭제 API·소유권 판정·실패 정책을 새로 정의해야 하므로 후속. 이번에는 자동 입장만 억제하고 방 목록 갱신으로 노출한다(Decision 5b)
  - 곡 목록 **행 자체의 접근성**(`div` + 중첩 클릭 요소인 별·삭제 아이콘) — 목록 리팩터링 건으로 분리
  - **`NavigationItem`의 포커스 가능화**(`NavigationItem.tsx:17`, 데스크톱 뒤로가기 트리거) — 사용처 3곳이 `<Link>`로 감싸져 있어(`AppShell.tsx:67,74,113`) 루트를 `<button>`으로 바꾸면 `<a><button></a>` 중첩이 된다. 폴리모픽(`as` prop) 전환 + `Link` 사용처 재구성이 필요해 네비게이션 전체에 파급되므로 별도 change. 그때까지 데스크톱 뒤로가기로 연 확인 모달은 닫을 때 포커스가 `<body>`로 복원된다(모바일은 이미 `<button>`이라 해당 없음)
  - `tracks.map`의 누락된 `key`(`PlaylistWriteView.tsx:230`), `deleteTrack`이 `representativeIndex === null`이면 조기 return해 삭제가 되지 않는 문제 — 별개 버그
  - `AudioGateOverlay`·`RoundResultOverlay`·`RoundRevealOverlay`의 `Modal` 통합 — 라운드 UI 관심사라 분리
