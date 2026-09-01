## Context

`app/components/ui/Modal.tsx`는 50줄짜리 프리미티브지만, 열림 상태를 **부모와 이중으로 소유**한다. 이 이중 소유가 재현된 버그(곡 추가 레이어 잠금)와 잠복 결함(비밀번호 모달)을 동시에 낳았다. 현재 코드의 문제 지점:

```
Modal.tsx:10-11   const [visible, setVisible] = useState(false)   ← 열림 여부의 두 번째 사본
                  const [closing, setClosing] = useState(false)

Modal.tsx:13-20   useEffect(..., [isOpen])                        ← isOpen이 안 바뀌면 실행되지 않음
Modal.tsx:22-24   handleClose = () => setClosing(true)            ← 부모에게 알리지 않고 스스로 닫음
Modal.tsx:26-32   handleAnimationEnd → onClose()                  ← 부모에겐 사후 통보만
Modal.tsx:34      if (!visible) return null
Modal.tsx:39      onClick={handleClose}                           ← 배경 클릭 진입점
```

소비자 4곳:

| 소비자 | 위치 | 현재 `onClose` | 증상 |
|---|---|---|---|
| 곡 추가·편집 | `PlaylistWriteView.tsx:266` | `() => {}` | **재현됨** — 배경 클릭 후 영구 잠금 |
| 뒤로가기 확인 | `PlaylistWriteView.tsx:256` | `setIsBackModalOpen(false)` | 정상 |
| 방 만들기 | `RoomCreate.tsx:148` | 부모의 `setShowCreate(false)` | 정상 |
| 비밀번호 입력 | `PasswordModal.tsx:30` | `isLoading`이면 **호출 안 함** | **잠복** — 연결 중 배경 클릭 시 잠금 |

"정상"인 두 곳도 우연히 정상일 뿐이다. `onClose`가 무조건 상태를 내리기 때문에 어긋나지 않을 뿐, 부모가 닫기를 **거부할 수단은 없다**. 그래서 거부가 필요했던 두 곳(`() => {}`, `isLoading` 가드)이 정확히 망가졌다.

## Goals / Non-Goals

**Goals**
- 열림 상태의 소유권을 부모 `isOpen` 하나로 통일해 두 곳의 굳음 결함을 구조적으로 제거
- 닫기 **요청**과 실제 닫힘을 분리하되, 요청의 수락/거부는 `Modal`이 `dismissible` 정책으로 결정하고 부모는 `onClose`를 **무조건 이행**한다(Decision 1)
- 어떤 경로로 닫히든(사용자 조작·브라우저 강제) `isOpen`과 실제 표시 상태가 어긋나지 않게 한다
- 키보드·스크린리더 접근성 확보(ESC·포커스 트랩·포커스 복원·배경 비활성화·접근 가능한 이름)
- 접근성 작업이 **같은 종류의 굳음 버그를 재도입하지 않도록** 언마운트 경로를 이벤트 단일 의존에서 떼어냄

**Non-Goals**
- 폼 더티 상태 확인 프롬프트(작성 중 내용 보호)
- 비밀번호 모달에 명시적인 '연결 취소' UI를 제공하는 것 — 연결 중에는 `dismissible={false}`로 닫기를 억제한다. 단 플랫폼이 강제로 닫는 경로가 실재하므로, **늦게 도착한 연결 성공을 무시하는 인-플라이트 가드는 이번 범위 안이다**(Decision 5a)
- 방 만들기 모달을 닫았을 때 **이미 생성된 방을 서버에서 되돌리는 것**(생성 취소·삭제) — 가드는 자동 입장만 억제하고 방은 남긴다. 대신 방 목록을 갱신해 사용자가 스스로 들어갈 경로를 남긴다(Decision 5b)
- 곡 목록 행·아이콘의 접근성 개선, 라운드 화면 오버레이의 `Modal` 통합
- `NavigationItem`의 포커스 가능화(데스크톱 뒤로가기 트리거) — `<Link>` 중첩 때문에 폴리모픽 전환이 필요해 별도 change(Decision 7 "범위 경계")
- 새 애니메이션 라이브러리 도입(`framer-motion`이 이미 있지만 쓰지 않는다)

## 상태 머신

핵심은 **`phase`가 열림 여부를 결정하지 않는다**는 것이다. 열림 여부의 유일한 진실은 부모의 `isOpen`이고, `phase`는 "언제 DOM에서 떼어낼지"만 담당하는 파생 상태다.

```
   isOpen (부모 소유 — 유일한 진실)
        │
        ├─ true  ──▶ 마운트 + showModal() + 등장 애니메이션
        └─ false ──▶ 퇴장 애니메이션 ──▶ close() ──▶ 언마운트

   phase (Modal 내부 — 지연 언마운트 전용)

        closed ──[isOpen false→true]──▶ open
          ▲                              │
          │                   [isOpen true→false]
          │                              ▼
          └──[animationend | timeout]── closing
                                         │
                          [isOpen 다시 true] ← 닫히는 중 재오픈
                                         │      (현재 앱에서는 도달 불가 — 아래 주석)
                                         │
                                         └──▶ open (스냅백, 애니메이션 반전)
```

**스냅백은 현재 앱의 조작으로는 도달할 수 없다.** `close()`는 퇴장 애니메이션이 끝난 시점에만 호출되므로(Decision 3), `phase === "closing"`인 0.2초 동안 `<dialog>`는 여전히 모달 상태다 — 배경 전체가 `inert`이고 `::backdrop`이 뷰포트를 덮으며, 패널 밖 클릭은 `<dialog>` 엘리먼트가 타깃으로 잡힌다(Decision 2 함정 1). 모달을 여는 트리거(곡 목록 행, '방 만들기' 카드, 방 카드)는 모두 `Modal`의 자손이 아니라 형제 서브트리에 있으므로(`PlaylistWriteView.tsx:166-255` vs `:256,266`, `RoomsView.tsx:97-195` vs `:196,209`), 퇴장 중 클릭은 배경 클릭 판정에 걸릴 뿐 `isOpen`을 다시 올리지 못한다. 따라서 **재오픈은 언제나 `phase === "closed"` 이후에 일어난다.** 위 전이는 부모가 프로그램적으로 `isOpen`을 되올리는 경우까지 `Modal`이 모순 없이 처리하기 위한 **방어적 전이**로만 남긴다(상태 머신의 완전성). 사용자에게 보이는 리셋 보장은 스냅백이 아니라 **"완전히 닫힌 뒤의 재오픈"** 경로에서 성립해야 하며, 스펙 시나리오와 수동 검증도 그 조작으로 기술한다.

닫기 **요청**은 `Modal`의 `dismissible` 정책을 통과할 때만 부모에게 올라간다. 올라간 요청은 부모가 반드시 이행한다:

```
   [배경 클릭]  ┐
   [ESC(cancel)]┼──▶ dismissible?
                ┘        ├─ false: 요청 폐기(+ cancel.preventDefault 시도) ──▶ 모달 유지
                         └─ true : onClose() ──▶ 부모가 setIsOpen(false) ──▶ 위 전이 시작

   [브라우저 강제 닫힘]  ──▶ <dialog> 'close' 이벤트 ──▶ 무조건 onClose()
   (cancel 없이 닫히는 경로)        (phase="closed" + 부모 isOpen 동기화)
```

거부가 **정상 동작**이라는 점이 지금과의 결정적 차이다. 다만 거부는 부모가 `onClose`를 무시하는 방식이 **아니라** `Modal`이 요청을 emit하지 않는 방식으로 표현된다 — `onClose={() => {}}` idiom은 폐기한다(Decision 1).

## Decision 1 — `isOpen`이 유일한 진실, 거부는 `dismissible`, `onClose`는 무조건 이행

**결정**: 세 가지를 함께 정한다.

1. **`Modal`은 스스로 닫지 않는다.** 배경 클릭·ESC는 `onClose()` **요청**으로만 라우팅되고, 실제 닫힘은 부모가 `isOpen`을 `false`로 내려 `props`가 바뀔 때 시작된다. `onClose`에서 "애니메이션 종료 통보" 의미는 제거하고, 언마운트는 `Modal` 내부에서 완결한다.
2. **거부는 `dismissible?: boolean`(기본 `true`) prop으로 선언한다.** `dismissible === false`면 `Modal`이 배경 클릭을 무시하고 `cancel`에 `preventDefault()`를 시도한다 — 즉 요청 자체가 부모에게 올라가지 않는다. 부모가 `onClose`를 no-op으로 두어 거부하는 idiom(`PlaylistWriteView.tsx:266`의 `onClose={() => {}}`)은 **폐기한다.**
3. **`onClose`는 "닫아라"라는 무조건 이행 신호다.** 부모는 `onClose`를 받으면 반드시 `isOpen`을 `false`로 내려야 한다. `Modal`은 사용자 조작뿐 아니라 **브라우저가 강제로 닫은 경우에도** `onClose`를 emit하므로(Decision 2의 "함정 4"), 부모가 이를 무시하면 그 순간 `isOpen`과 표시 상태가 어긋나 이번에 고치려는 굳음 버그가 그대로 재생산된다.

**왜 거부를 부모가 아니라 `Modal`이 정하나**: `<dialog>`는 close watcher 남용 방지 규칙 때문에 `cancel` 없이 네이티브로 닫히는 경로가 실재한다(Decision 2 함정 4). 그 경로에서 "부모가 거부"는 곧 상태 어긋남이다. 거부를 `Modal`이 emit 여부로 결정하는 정책(`dismissible`)으로 올리고, 동기화는 무조건 경로(`onClose`)로 분리하면 — 거부는 최선 노력으로 남되 **동기화는 항상 성립한다.**

**왜 지금 구조가 틀렸나**: 열림 여부라는 **하나의 사실**이 두 곳에 저장되면 둘이 어긋나는 순간이 반드시 온다. 현재 코드는 그 어긋남을 만드는 경로(배경 클릭)를 열어두고, 되돌릴 경로(`isOpen`을 다시 내리는 것)는 `onClose`를 반드시 이행하는 부모에게만 열어뒀다. 이행하지 않는 부모가 둘 있었고 둘 다 망가졌다.

**왜 `onClose`에서 애니메이션 통보를 떼어내나**: 부모가 "언제 퇴장 애니메이션이 끝났는지" 알아야 할 이유가 없다. 그건 `Modal`의 렌더링 세부사항이다. 이 통보를 부모에게 넘긴 것이 겸직의 시작이었고, 겸직이 있는 한 부모는 "닫아달라"와 "다 닫혔다"를 구분할 수 없어 어느 한쪽을 반드시 오해한다.

**대안(기각)**: 열림 상태를 `Modal`이 전부 소유하는 **uncontrolled** 모달(`<Modal trigger={...}>`). 소유권이 한 곳이 되므로 어긋남은 사라지지만, 곡 편집 모달은 열릴 때 어떤 트랙을 편집할지(`selectedTrack`·`selectedTrackIndex`)를 부모가 함께 세팅해야 하고 저장 성공 시 부모가 닫아야 한다 — 열림 시점이 부모의 다른 상태와 얽혀 있어 uncontrolled는 맞지 않는다.

## Decision 2 — 커스텀 `div`가 아니라 네이티브 `<dialog>` + `showModal()`

**결정**: 오버레이를 `<dialog>` 엘리먼트로 바꾸고 `showModal()`로 연다.

**왜**: 요구된 접근성 항목 대부분을 브라우저가 이미 정확하게 구현해 두었다.

| 요구 | 커스텀 `div` | 네이티브 `<dialog>` |
|---|---|---|
| 포커스 트랩(Tab 순환) | 직접 구현 | **브라우저 제공** |
| 배경 요소 비활성화 | `inert` 수동 관리 | **자동** |
| 닫을 때 포커스 복원 | 트리거 ref 저장·복원 | **자동** |
| ESC | `keydown` 리스너 직접 | `cancel` 이벤트 |
| top-layer / `z-index` | 수동 관리 | **자동**(단 모달 **위**에 떠야 하는 전역 UI는 함께 승격해야 한다 — Decision 8) |
| 배경 스크롤 잠금 | 직접 | 직접(동일하게 필요) |

포커스 트랩은 직접 짜면 늘 새는 곳이 생긴다(동적으로 나타나는 요소, Shift+Tab 경계, iframe). 특히 **곡 추가 레이어 안에는 `react-youtube`가 만드는 iframe이 있어** 커스텀 트랩으로는 다루기 까다롭다. 브라우저 구현은 이걸 이미 맞게 처리한다.

부수 효과로 **`position: fixed`의 취약함에서 벗어난다.** 현재 오버레이는 `fixed inset-0`인데, 조상에 `transform`·`filter`·`will-change`가 걸리면 그 조상이 컨테이닝 블록이 되어 오버레이가 어긋난다. 모달들은 `AppShell`의 `AnimatedContent`(framer-motion `motion.div`, `AppShell.tsx:17-25`) 안에 렌더되므로 라우트 전환 애니메이션 중 이 조건에 노출될 여지가 있다. `<dialog>`의 top-layer는 조상 스택 컨텍스트와 무관하게 승격되므로 이 위험이 사라진다. (현재 실제 어긋남이 관측된 것은 아니며, 구조적 위험의 제거로만 계산한다.)

**구현상 함정 네 가지**:

1. **배경 클릭 판정** — `::backdrop` 클릭은 `<dialog>` 엘리먼트를 타깃으로 잡힌다. 따라서 `<dialog>` 자체에는 패딩·배경·크기를 주지 않고 안쪽 래퍼 div가 시각을 담당해야 `e.target === dialogRef.current`가 "배경을 눌렀다"와 정확히 일치한다. `<dialog>`에 패딩이 있으면 패딩 클릭이 배경 클릭으로 오판된다.
2. **StrictMode 이중 이펙트** — `front/CLAUDE.md`의 "알려진 함정"대로 개발 모드에서 이펙트가 두 번 실행된다. 이미 열린 `<dialog>`에 `showModal()`을 다시 호출하면 `InvalidStateError`가 나므로 `if (!dialog.open) dialog.showModal()` 가드를 반드시 둔다. `close()`도 대칭으로 `if (dialog.open)` 가드.
3. **초기 포커스를 React `autoFocus` prop으로는 고정할 수 없다** — `<dialog>`가 `showModal()`로 열릴 때 브라우저는 dialog focusing steps를 실행해, DOM에 `autofocus` **속성**을 가진 요소가 없으면 **트리 순서상 첫 번째 포커스 가능한 자손**에 포커스를 준다(그 규칙의 세부는 브라우저마다 미묘하게 갈린다). 그런데 React DOM은 `autoFocus` prop을 **DOM 속성으로 내보내지 않는다** — `setProp`에서 `case 'autoFocus'`는 no-op이고, 대신 commit 시점에 `element.focus()`를 호출하는 폴리필로 대체한다. 이 구조에서 그 폴리필은 무효다: children이 마운트되는 commit 시점의 `<dialog>`는 아직 `showModal()` 전이라 UA 스타일상 `display: none`이고, 그 서브트리는 포커스를 받을 수 없다. 결과적으로 `autoFocus`를 붙여도 초기 포커스는 "첫 번째 포커스 가능한 자손"으로 떨어진다 — 뒤로가기 확인 모달은 **파괴적인 '확인' 버튼**(`PlaylistWriteView.tsx:261`이 취소 262보다 앞), 곡 편집 레이어는 URL이 이미 있을 때 **YouTube iframe**(`TrackEditLayer.tsx:161`이 URL 입력 171-175보다 앞)이 된다.

   **결정**: `Modal`에 `initialFocusRef?: RefObject<HTMLElement | null>` prop을 두고, **`showModal()` 호출 직후** 그 ref가 가리키는 요소에 명령형으로 `focus()`를 준다. ref가 없으면 브라우저 기본 규칙에 맡긴다. 타이밍 소유권이 `Modal`의 열림 이펙트 한 곳에 모이므로 "`showModal()` 전이라 무효"라는 실패 양식이 구조적으로 불가능해진다(Decision 6 참조).

   **대안(기각) — 리터럴 `autofocus` 속성을 DOM에 내보내기**(`{...{autofocus: ""}}` 같은 우회로 React의 특수 처리를 피하는 방식): 프레임워크가 의도적으로 가로채는 prop을 뒷문으로 통과시키는 것이라 React 버전 변화에 취약하고, 초기 포커스의 책임이 `Modal`이 아니라 소비자 JSX 곳곳에 흩어져 위 실패 양식을 다시 만들기 쉽다.
4. **브라우저가 `cancel` 없이 `<dialog>`를 닫는 경로가 있다** — 아래에서 따로 다룬다. 이것이 이 설계에서 가장 위험한 함정이다.

**함정 4 — close watcher 남용 방지 규칙**

`<dialog>`의 ESC 처리는 close watcher 위에 얹혀 있고, close watcher에는 "페이지가 사용자를 가둘 수 없게" 하는 남용 방지 규칙이 있다. WICG close-watcher 명세에 따르면 `cancel`은 **transient user activation이 남아 있을 때만** 발화하고, 한 번 발화해 소비되면 새 사용자 상호작용 전까지 다시 발화하지 않는다. 또 "사용자가 중간 활성화 없이 연속으로 두 번 닫기 요청을 보내면 그 요청은 반드시 통과한다". 따라서 `cancel`을 `preventDefault()`로 가로채는 것만으로는 부족하고, 다음 두 경로에서 **`<dialog>`가 `cancel` 없이 네이티브로 닫힌다**:

1. **활성화 만료 후 첫 ESC** — 모달을 연 뒤 아무 조작 없이 시간이 지나고(뒤로가기 확인 문구를 읽는 중, 비밀번호 모달에서 연결을 기다리는 중 — 이때 입력·버튼이 전부 `disabled`라 활성화를 갱신할 조작 자체가 없다) ESC를 누르는 경우. ESC 키다운 자체는 활성화를 부여하지 않는다.
2. **연속 두 번째 ESC** — 첫 ESC를 `preventDefault()`로 막으면 활성화가 소비되고, 사이에 다른 조작 없이 누른 두 번째 ESC는 그대로 통과한다. 닫히지 않아 한 번 더 누르는 것은 사용자의 가장 자연스러운 반응이다.

이 경로에서는 `cancel` 핸들러가 돌지 않으므로 `onClose()`도 호출되지 않는다. 그러면 네이티브 `<dialog>`는 `open === false`가 되어 화면에서 사라지는데 `phase`는 `"open"`, 부모 `isOpen`은 `true`로 남는다 — 같은 트리거를 다시 눌러도 `true → true`라 재오픈 계기가 없고, **이번 change가 없애려는 굳음 버그가 네 모달 전부에서 재도입된다.**

**결정**: 네이티브 `close` 이벤트를 **권위 있는 신호**로 삼는다. `Modal`은 `<dialog>`의 `close`를 구독해 그 시점에 `phase`를 `"closed"`로 내리고, 부모에게 `onClose()`를 호출해 `isOpen`을 동기화시킨다. 즉 **강제 닫힘을 수락으로 간주한다.** 정상 종료 경로(배경 클릭·`cancel` → 부모가 `isOpen`을 내림 → 퇴장 애니메이션 → `Modal`이 스스로 `close()` 호출)에서도 `close`가 발화하므로, `onClose`가 중복 호출되지 않도록 "이미 부모 주도로 닫히는 중"인지를 가드한다.

**대안(기각) — 거부 관철(강제 닫힘을 만나면 `showModal()`로 재오픈)**: `cancel`의 취소 가능성이 활성화로 제한되는 것은 플랫폼이 의도적으로 만든 "사용자를 모달에 가두지 못하게 하는" 보장이다. 재오픈은 그 보장과 정면으로 싸운다. 게다가 ESC는 활성화를 부여하지 않으므로 사용자가 ESC를 반복해도 모달이 깜빡이기만 하고 닫히지 않는 상태에 빠질 수 있다 — 이번 change가 요구받은 "ESC로 닫힌다"와 정반대 결과다.

**대안(기각) — `closedby="none"`**: 브라우저 기본 닫기를 아예 끄는 속성이지만 지원 기준선이 최근이라 폴백이 반드시 필요하고, 그 폴백이 결국 위 두 안 중 하나이므로 복잡도만 더한다.

**대안(기각)**: 커스텀 `div`를 유지하고 포커스 트랩·`inert`·ESC를 직접 구현. 코드가 더 많고 iframe 경계에서 새기 쉽다. 헤드리스 라이브러리(Radix·Headless UI) 도입도 가능하나, 브라우저가 공짜로 주는 것을 위해 의존성을 추가할 이유가 없다.

## Decision 3 — 언마운트는 `animationend` **또는** 타임아웃, 둘 중 먼저 오는 쪽

**결정**: 퇴장 완료 판정을 `animationend` 이벤트 단독에 의존하지 않는다. 애니메이션 지속시간 + 여유를 둔 타임아웃을 함께 걸고, 먼저 도착하는 신호로 `close()` + 언마운트한다. 그리고 `prefers-reduced-motion: reduce`에서는 `animation: none`이 **아니라** `animation-duration: 0.01ms`를 쓴다.

**왜 이게 중요한가**: 이번 change는 접근성 작업을 포함하고, 접근성 작업의 관용적 첫걸음은 `prefers-reduced-motion`에서 애니메이션을 끄는 것이다. 그런데 `animation: none`이면 **`animationend`가 발생하지 않는다.** 지금 코드 구조 그대로였다면 그 순간 모달은 영영 언마운트되지 않는다 — **우리가 고치려는 바로 그 굳음 버그가, 접근성을 넣는 손으로 재도입된다.** 지속시간을 0에 가깝게 두면 이벤트는 정상 발생하고, 타임아웃 폴백은 그 밖의 미지의 경로(탭 백그라운드화, 브라우저 구현 차이)까지 덮는다.

```
   퇴장 시작 ──┬─▶ animationend  ─┐
               │                  ├─▶ 먼저 온 쪽에서 close() + phase="closed"
               └─▶ setTimeout    ─┘     (양쪽 모두 취소·정리)
                   (duration + 여유)
```

**대안(기각)**: `transition` + `transition-behavior: allow-discrete` + `@starting-style`로 CSS만으로 처리. 최신 브라우저에선 우아하지만 Firefox 129+가 필요해 Tailwind v4가 요구하는 기준선보다 앞서고, 언마운트 시점은 여전히 JS가 알아야 한다.

## Decision 4 — 곡 추가·편집 레이어는 배경 클릭·ESC로 닫힌다

**결정**: 곡 편집 `Modal`의 `onClose`를 `() => setIsOpenEditTrack(false)`로 연결한다. 배경 클릭과 ESC 모두 닫힘으로 이어지고, 작성 중이던 입력은 버려진다.

**왜**: 사용자가 명시적으로 선택한 동작이다. `() => {}`가 원래 의도한 바("배경 클릭으로 닫히지 않게")는 이제 `dismissible={false}`로 표현할 수 있지만(Decision 1), 요청은 "닫히고 다시 열리는 것"이므로 기본값 `dismissible`(=`true`)을 그대로 쓴다.

**받아들이는 트레이드오프**: 곡 추가 레이어는 URL·제목·재생 구간·반복 횟수·추가 정답까지 입력이 많은 폼이라, 배경을 잘못 눌러 전부 잃을 수 있다. 뒤로가기에는 이미 확인 모달이 있는데 폼이 더 무거운 이 레이어는 보호가 없다는 비대칭이 남는다. 더티 상태 확인 프롬프트는 모달 위 모달을 요구하고 "더티" 판정 기준(초기값 대비 변경 여부)을 새로 정의해야 해 이번 범위 밖으로 둔다. **Decision 1의 구조 덕분에 이 후속 작업은 `Modal` 수정 없이 `dismissible={!isDirty}`(또는 확인 모달을 띄우는 래퍼)로 가능하다** — 닫기 요청을 조건부로 억제하는 것이 이제 일급 prop이기 때문이다.

**재오픈 시 자식 상태**: `TrackEditLayer`는 `useState(props.title)` 식으로 props에서 **최초 한 번만** 초기화된다(`TrackEditLayer.tsx:37-43`). 따라서 곡 A 레이어를 닫고 곡 B를 클릭해 다시 열었을 때 이전 입력이 남지 않으려면 children이 새로 마운트되어야 한다. 이를 보장하는 것은 **`phase === "closed"`면 children을 렌더하지 않는다**(현재의 `if (!visible) return null`과 같은 효과)는 규칙이다 — 재오픈은 언제나 `phase === "closed"` 이후에만 일어나므로(상태 머신 절의 "스냅백은 도달 불가"), 도달 가능한 모든 경로에서 children은 이미 새로 마운트된다. `Modal`이 **열림마다 증가하는 내부 키**를 children 래퍼에 부여하는 것은 그 위에 얹는 **방어적 장치**다 — 부모가 프로그램적으로 퇴장 중 `isOpen`을 되올리는 경우(스냅백 전이)에도 같은 결과가 나오게 만든다. 비용이 한 줄이고 `Modal` 상태 머신을 자기완결적으로 만들어 주므로 유지하되, **사용자에게 보이는 보장의 근거는 언마운트 규칙**임을 분명히 한다.

**단, 이 보장의 범위는 children 서브트리로 한정된다.** 내부 키는 `Modal`의 children 안에 있는 state만 리셋한다. 소비자가 폼 state를 `Modal`보다 **위에서** 들고 있으면 키를 증가시켜도 그 값은 그대로 다시 렌더된다. ("소비자가 잊을 수 없다"는 근거는 성립하지 않는다 — state가 `Modal` 위에 있으면 내부 키는 조용히 아무 일도 하지 않으므로, 이 경계를 계약으로 명시하는 것으로 대신한다.) 따라서 `Modal`의 계약은 다음과 같이 갈린다.

| 소비자 | 폼 state 위치 | 재오픈 시 리셋 책임 |
|---|---|---|
| `TrackEditLayer` | `Modal`의 children 안(`TrackEditLayer.tsx:37-43`) | **`Modal`이 자동 보장** — `phase === "closed"`에서 children 언마운트(+ 방어적 내부 키) |
| `RoomCreate` | `Modal`보다 위(`RoomCreate.tsx:22-41`) | **소비자 책임** — `RoomCreate`는 `RoomsView`에 **항상 마운트**된 채(`RoomsView.tsx:196-208`) 폼 state를 들고 있어, 스냅백이 아니라 **평범한 닫기 후 재오픈**에서도 직전 입력이 그대로 남는다. `isOpen`이 `false→true`가 될 때 폼 state를 초기화한다(`tasks.md` 4.7) |
| `PasswordModal` | `Modal`보다 위(`PasswordModal.tsx:13`) | **소비자 책임** — 닫기 핸들러의 `setPassword("")`(`:24`)가 이미 담당(`isLoading` 가드는 `dismissible`로 이관 — Decision 5a) |
| 뒤로가기 확인 | 보유 state 없음 | 해당 없음 |

`RoomCreate`·`PasswordModal`을 children 안으로 밀어 넣거나 부모가 `key`를 주도록 강제하는 대안도 가능하지만, 전자는 소비자 컴포넌트 구조를 다시 짜는 일이고 후자는 모든 소비자에게 매번 `key` 관리를 요구한다. 소비자가 4곳뿐이고 그중 리셋이 필요한 곳이 실질적으로 `RoomCreate` 하나이므로, 계약을 좁히고 그 한 곳을 `tasks.md`에 명시하는 편이 비용이 낮다.

## Decision 5 — in-flight 작업을 가진 모달은 `dismissible`(최선 노력) + in-flight 가드를 함께 둔다

**공통 규칙**: 모달이 **닫힌 뒤에 완료될 수 있는 비동기 작업**을 들고 있으면 두 가지를 함께 둔다.

1. 작업이 도는 동안 `dismissible={false}`로 닫기 요청을 억제한다 — 배경 클릭은 확실히 막히고, ESC는 **최선 노력**이다(Decision 2 함정 4).
2. 억제가 뚫릴 것을 전제로 **in-flight 가드**를 둔다 — 모달이 닫힌 뒤 도착한 결과는 후속 부수 효과(화면 이동 등)를 실행하지 않는다.

억제만으로 끝내지 않는 이유는 Decision 2 함정 4다. 플랫폼이 강제로 닫는 경로가 실재하는 이상 "닫히지 않는다"에 기대어 뒷정리를 미룰 수 없다. 게다가 이번 change는 ESC 닫기를 새로 부여해(현재 `Modal.tsx`에는 ESC 처리가 없다) 이탈 경로를 **넓힌다.**

해당 모달은 둘이며, **부수 효과를 되돌릴 수 있는지**가 갈린다.

| 모달 | in-flight 작업 | 닫힌 뒤 결과가 도착하면 | 가드가 하는 일 | 되돌릴 수 있나 |
|---|---|---|---|---|
| `PasswordModal` | STOMP `connectToRoom` | `setConnection` + `navigate` | 결과 폐기 + `client.deactivate()` | **예** — 연결을 끊으면 흔적이 남지 않는다 |
| `RoomCreate` | HTTP `createRoom` | `onCreated` → `connectToRoom` → `navigate` | `onCreated` 미호출(**자동 입장만** 억제) + 방 목록 갱신 | **아니오** — 방은 이미 서버에 생성됐다 |

### 5a — 비밀번호 모달(연결): 억제 + 연결 정리

**결정**: `PasswordModal.tsx:22`의 `isLoading` 가드를 닫기 핸들러에서 걷어내고 **`<Modal dismissible={!isLoading}>`** 로 옮긴다. 닫기 핸들러는 `setPassword("")` + `onClose()`를 **무조건** 수행한다(Decision 1의 "`onClose`는 무조건 이행").

**보장 수준**: 배경 클릭은 확실히 억제된다(`Modal`이 자기 DOM 이벤트를 무시하면 그만이다). **ESC는 최선 노력이다** — `cancel`을 `preventDefault()`로 막는 것은 transient user activation이 남아 있을 때만 통하고, 그 밖의 경로에서는 플랫폼이 강제로 닫는다(Decision 2 함정 4). 특히 이 모달은 연결 대기 중 입력·버튼이 전부 `disabled`(`PasswordModal.tsx:33-58`)라 활성화를 갱신할 조작이 없어, **강제 닫힘이 오히려 잘 일어나는 조건**이다. 그때는 네이티브 `close` → `onClose()` → `isOpen=false`로 정상 닫히며, 상태는 어긋나지 않는다.

**왜 사용자 조작으로 닫기를 열어주지 않나**: 명시적 '연결 취소' UI를 제공하려면 취소 UX·에러 문구·재시도 흐름을 새로 정의해야 한다. 대기 상태는 **`CONNECT_TIMEOUT_MS = 5000`(`stomp.ts:3`)으로 상한이 있어** 최대 5초 뒤 반드시 실패 또는 성공으로 빠져나오므로, 억제를 기본값으로 두는 편이 단순하다.

**인-플라이트 가드는 이번 범위 안이다**: 강제 닫힘이 실재하는 이상 "연결 중에는 절대 닫히지 않는다"에 기대어 뒷정리를 미룰 수 없다. 모달이 닫힌 뒤 늦게 도착한 성공이 `setConnection` + `navigate`를 실행하면 **사용자가 빠져나온 방으로 몇 초 뒤 튕겨 들어간다.** 따라서 `RoomsView.tsx:77-92`의 `handlePasswordSubmit`에 **세대 토큰(또는 ref) 가드**를 두어, 모달이 닫힌 뒤 도착한 결과는 무시하고 `client.deactivate()`로 연결을 정리한다. 이 항목은 원래 후속 과제로 뺐던 것이지만 본 결정으로 전제가 무너졌으므로 범위 안으로 들인다(`tasks.md` 4.8).

### 5b — 방 만들기 모달(생성): 억제 + 자동 입장만 억제

**결정**: `RoomCreate`의 `<Modal>`에 **`dismissible={!isCreating}`**을 넘기고, 생성 요청에 **in-flight 가드**를 둔다. 모달이 닫힌 뒤 도착한 `createRoom` 성공은 `onCreated`를 호출하지 않아 `connectToRoom` → `navigate`로 이어지지 않게 한다. 가드는 `RoomCreate` 내부에 둔다 — 요청 시점의 세대 토큰(또는 ref)을 `isOpen` 변화와 대조하는, `PasswordModal`+`RoomsView` 조합과 **같은 패턴**이다.

**왜 필요한가**: `RoomCreate`는 `PasswordModal`과 **구조적으로 같은** in-flight 부수 효과를 갖는다. '만들기'를 누르면 `isCreating=true`로 두고 `createRoom`을 await한 뒤 `onClose()` → `onCreated(room.id, ...)`를 호출하고(`RoomCreate.tsx:307-318`), `RoomsView`의 `onCreated`가 `connectToRoom` → `setConnection` → `navigate('/rooms/:id')`를 실행한다(`RoomsView.tsx:196-208`). 느린 네트워크에서 생성이 도는 동안 배경 클릭·ESC로 모달을 닫으면, 몇 초 뒤 **사용자가 취소한 방으로 튕겨 들어간다.** 억제(`dismissible`)만으로는 부족한 것도 같은 이유다 — 억제는 최선 노력이라 플랫폼이 강제로 닫는다.

**되돌릴 수 없는 서버 부수 효과(명시)**: `createRoom`은 응답이 온 시점에 **이미 서버에 방을 만들었다.** 가드가 하는 일은 "생성 취소"가 아니라 **자동 입장 억제**뿐이며, 방은 그대로 남는다. 그래서 가드가 걸린 경로에서는 **방 목록을 갱신해** 사용자가 만들어진 방을 목록에서 보고 스스로 들어갈 수 있게 한다. 목록은 `RoomsView`가 마운트 시 한 번 `fetchRooms()`로 채우고(`RoomsView.tsx:31-42`) 그 뒤 갱신하지 않으므로, 가드가 걸렸을 때만 부모에게 목록 갱신 신호를 올려 다시 불러온다(정상 경로에서는 곧바로 `navigate`하므로 갱신이 필요 없다). 신호를 어떤 수단으로 올릴지(전용 콜백 prop 등)는 구현 시 정한다.

**서버 롤백(생성한 방 삭제)은 하지 않는다**: 방 삭제 API·소유권 판정·실패 시 재시도 정책을 새로 정의해야 하고, 사용자가 "만들기"를 누른 의도 자체는 유효하다. 만들어진 방을 목록에 보여 주는 편이 단순하고 손실이 없다(`Non-Goals` 참조).

## Decision 6 — 접근 가능한 이름은 `ModalTitle` + `useId`

**결정**: `Modal`이 `useId()`로 제목 id를 만들어 컨텍스트로 내려주고, `<dialog aria-labelledby={titleId}>`가 이를 가리킨다. 소비자는 자기 제목을 `<ModalTitle>`로 렌더한다.

```
   Modal (useId → titleId, context.Provider)
     └─ <dialog role="dialog" aria-labelledby={titleId}>
          └─ children
               └─ <ModalTitle>곡 추가</ModalTitle>  →  <h2 id={titleId}>곡 추가</h2>
```

**왜 문자열 `ariaLabel` prop이 아닌가**: 곡 편집 레이어의 제목은 `isEditing() ? "곡 편집" : "곡 추가"`로 **동적**이고(`TrackEditLayer.tsx:153`), 그 판단은 자식 안에 있다. 문자열 prop이면 부모(`PlaylistWriteView`)가 같은 삼항을 중복 작성해야 하고, 한쪽만 바뀌는 순간 **화면에 보이는 제목과 스크린리더가 읽는 이름이 어긋난다.** id 참조 방식은 구조적으로 어긋날 수 없다.

**덤으로 얻는 것**: 지금 세 소비자의 제목 마크업이 제각각이다 — `<h2 className="text-xl font-bold mb-4">`(`TrackEditLayer.tsx:153`, `PlaylistWriteView.tsx:258`), `<h3 className="text-lg font-bold text-zinc-100 mb-4">`(`PasswordModal.tsx:32`), 그리고 **헤딩조차 아닌** `<div className="text-xl font-bold mb-6 ...">`(`RoomCreate.tsx:150`). `ModalTitle`이 이를 하나로 정리하고, 헤딩이 아니던 곳도 헤딩이 된다.

**리스크와 완화**: 소비자가 `ModalTitle`을 빠뜨리면 `aria-labelledby`가 존재하지 않는 id를 가리켜 접근 가능한 이름이 사라진다. 소비자가 4곳뿐이고 전부 이번에 수정하므로 실제 위험은 낮다. `tasks.md`에서 4곳 모두를 명시적 항목으로 둔다.

**초기 포커스와의 관계**: 초기 포커스는 `Modal`의 `initialFocusRef`로 고정한다(Decision 2 함정 3 — React `autoFocus` prop은 `<dialog>`에서 동작하지 않는다). 소비자별 대상:

| 모달 | `initialFocusRef` 대상 | 지정하지 않으면 |
|---|---|---|
| 곡 추가·편집 레이어 | YouTube URL `<input>`(`TrackEditLayer.tsx:171-175`) | URL이 이미 있으면 iframe(`:161`)이 트리 순서상 앞서 **교차 출처 iframe 안**으로 포커스가 들어간다 |
| 비밀번호 입력 | 비밀번호 `<input>`(`PasswordModal.tsx:39`) | 우연히 첫 포커스 가능 자손이라 맞지만, 마크업이 바뀌면 조용히 어긋난다 |
| 뒤로가기 확인 | **취소**(비파괴) 버튼(`PlaylistWriteView.tsx:262`) | 파괴적인 '확인'(`:261`)이 트리 순서상 앞서, 열자마자 Enter를 누르면 편집 내용이 버려진다 |
| 방 만들기 | 지정하지 않음 | 첫 포커스 가능 자손이 플레이리스트 탭의 '즐겨찾기' 버튼(`RoomCreate.tsx:157-165`)이다. 비파괴적이고 폼 맨 앞이라 기본값을 그대로 둔다 |

## Decision 7 — 모달 트리거 중 중첩 없는 3곳을 `<button>`으로 바꾼다

**결정**: `onClick` 달린 `<div>` 트리거 중 **중첩 인터랙티브 요소가 없는 3곳**을 `<button type="button">`으로 교체한다 — 곡 추가 '+'(`PlaylistWriteView.tsx:218`), 방 만들기 카드(`RoomsView.tsx:131`), 방 카드(`RoomsView.tsx:144`).

**왜 이것이 이번 범위인가**: 곁가지 정리가 아니라 **Decision 2가 성립하기 위한 전제**다. `<dialog>`가 닫힐 때 포커스를 복원하는 대상은 "열기 직전에 포커스를 갖고 있던 요소"인데, 지금 이 트리거들은 포커스를 받을 수 없으므로 그 대상이 `<body>`가 된다. 즉 키보드 사용자는 모달을 닫는 순간 페이지 맨 위로 되돌아간다 — 포커스 복원을 구현하고도 이득이 없다. 애초에 Tab으로 도달할 수도 Enter로 누를 수도 없어 키보드만으로는 곡을 추가할 수도, 방에 입장할 수도 없다는 문제도 함께 해소된다. 특히 **방 카드는 비밀번호 모달의 유일한 진입 경로**(`RoomsView.tsx:60`)라, 포함하지 않으면 그 모달을 키보드로 열 방법이 아예 없다.

세 곳 모두 자식이 `div`·`img`·`svg`·`span`뿐이라 중첩 인터랙티브 요소가 없어 단순 치환으로 끝난다.

**범위 경계 — 이번에 다루지 않는 트리거 2곳**:

- **곡 목록 행**(`PlaylistWriteView.tsx:230-250`): `onClick` 달린 `<div>` 안에 별·삭제 아이콘이 중첩 클릭 요소로 들어 있다. 버튼 안에 버튼을 넣을 수 없어 행 구조 자체를 다시 짜야 하므로 별도 change로 둔다.
- **데스크톱 뒤로가기**(`NavigationItem.tsx:17`, `AppShell.tsx:114-121`에서 사용): `NavigationItem`은 네비게이션 전역이 공유하는 프리미티브이고, 사용처 3곳이 `<Link>`로 감싸져 있다(`AppShell.tsx:67,74,113`). 루트를 `<button>`으로 바꾸면 `<a><button></a>` 중첩이 되므로, 폴리모픽(`as` prop) 전환과 `Link` 사용처 재구성이 함께 필요하다. 파급 범위가 네비게이션 전체라 별도 change로 둔다. **그 결과 데스크톱에서 뒤로가기로 연 확인 모달은 닫을 때 포커스가 `<body>`로 복원되는 한계가 이번 change 이후에도 남는다** (모바일 뒤로가기는 이미 `<button>`이라 해당 없음 — `MobileHeader.tsx:34-40`).

이 경계에 맞춰 `specs/modal-dialog/spec.md`의 트리거 요구사항도 이번 change가 실제로 다루는 3곳으로 한정해 서술한다 — 구현 완료 시점에 스펙이 참이 되도록.

## Decision 8 — 전역 토스트도 top layer로 올린다(모달보다 나중에 승격)

**결정**: `root.tsx:38`의 sonner `<Toaster />`를 top layer로 올리고(`popover="manual"` + 마운트 시 `showPopover()`), **모달이 열릴 때마다 재승격**한다.

**왜 이게 이번 change의 문제인가**: `showModal()`은 `<dialog>`와 `::backdrop`을 top layer로 올린다. top layer의 요소는 일반 레이어의 **모든** 요소 위에 그려지고, 그 순서는 `z-index`로 뒤집을 수 없다. 지금 `Modal` 오버레이에는 `z-` 유틸이 아예 없어(`Modal.tsx:36-48`) 일반 레이어 안에서 나중에 그려지는 `<Toaster />`(`root.tsx:38`, `<body>` 끝)가 모달 위에 정상적으로 보인다. `<dialog>`로 바꾸는 순간 이 관계가 뒤집혀 **토스트가 `bg-black/60 backdrop-blur-sm` 배경 아래로 내려가 어두워지고 흐려진다.**

구체적 회귀: `RoomCreate`의 방 생성 실패 경로는 **모달을 연 채** `toast.error("방 생성에 실패했습니다.")`만 호출하고 `onClose()`를 부르지 않는다(`RoomCreate.tsx:320`). 즉 이 경로의 **유일한** 실패 피드백이 이번 change로 사실상 보이지 않게 되어, 사용자는 '만들기'를 눌러도 아무 일도 일어나지 않는 것처럼 느낀다. 이는 Decision 2 비교표가 "top-layer / `z-index` — 자동"이라고 적은 항목의 반대 방향이므로, 같은 자리에서 함께 결정해야 한다.

**승격 순서 함정**: top layer는 `z-index`가 아니라 **승격 순서**로 쌓인다. 앱 마운트 시점에 한 번 올려둔 Toaster popover 위에 나중에 `showModal()`된 `<dialog>`가 얹히므로, **한 번 올리는 것만으로는 여전히 가려진다.** 따라서 모달이 열린 뒤 Toaster를 다시 올려야 한다 — `hidePopover()` → `showPopover()`가 top layer 재승격을 일으킨다. 재승격 시점은 `Modal`이 소유한다: `showModal()` 직후 재승격 신호를 보내고(커스텀 이벤트 또는 전용 훅) Toaster 래퍼가 이를 받아 재승격한다. 모달이 연속으로 여러 번 열려도 항상 마지막 모달 위에 오도록 **매 열림마다** 수행한다.

**배경 조작을 막지 않게**: popover 컨테이너는 화면 하단 영역을 차지하므로 `pointer-events: none`으로 두고 개별 토스트만 `auto`로 되돌린다. popover 기본 UA 스타일(`inset: auto`, `border`·`padding`·`margin`, `overflow`)을 초기화해 기존 토스트 위치(`position="bottom-center"`)를 그대로 유지한다.

**지원 기준선과 미지원 폴백**: Popover API(`popover` 속성 · `showPopover()`)는 Chrome 114 · Safari 17 · Firefox 125부터 쓸 수 있어, 이 프로젝트가 Tailwind v4 때문에 이미 요구하는 기준선(Chrome 111 · Safari 16.4 · Firefox 128)보다 Chrome·Safari 쪽이 조금 앞선다. 이 문서가 `closedby="none"`(Decision 2)과 `transition-behavior: allow-discrete`(Decision 3)를 기준선을 근거로 기각한 것과 **같은 잣대를 여기에도 적용한다** — 다만 여기서는 기각이 아니라 **채택하되 feature detection으로 감싼다**: `typeof el.showPopover === "function"`이 거짓이면 승격도 재승격도 건너뛰고 종전대로 일반 레이어에 렌더한다. 그 브라우저에서는 모달 위 토스트가 다시 가려지지만 이는 **이 결정 이전 상태와 동일**하므로 회귀가 아니다. 가드는 선택이 아니라 필수다 — `<Toaster />`는 앱 셸에 상시 렌더되므로(`root.tsx:38`) 마운트 이펙트에서 던진 `TypeError` 하나가 화면 전체를 날린다.

**남는 한계(수용)**: 모달 `<dialog>`가 열려 있는 동안에는 그 다이얼로그의 자손이 아닌 모든 요소가 플랫폼에 의해 inert가 되므로, top layer로 올린 토스트도 **보이기는 하되 클릭으로 닫을 수는 없다.** 이 앱의 토스트는 `duration={3000}`으로 자동 소멸하는 순수 피드백이고(`Toast.tsx:1-23`), 이번에 살리려는 것은 "실패를 알아차리는 것"이므로 가시성 회복으로 충분하다고 본다.

**대안(기각) — `RoomCreate` 실패를 모달 내부 인라인 에러로 바꾼다**: 그 한 경로는 고쳐지지만 "모달이 열린 동안 발생한 전역 알림이 안 보인다"는 문제 자체는 남는다. 이후 모달 안에서 토스트를 쓰는 코드가 추가될 때마다 같은 함정을 다시 밟는다. (인라인 에러 도입 자체는 이 결정과 배타적이지 않으며, 별도 UX 개선으로 남긴다.)

## 위험과 완화

| 위험 | 완화 |
|---|---|
| `onClose` 의미 변경이 미이행 호출부를 남김 | 소비자가 4곳뿐이고 전부 이번에 수정. TypeScript 시그니처는 그대로라 컴파일러가 잡아주지 않으므로 `tasks.md`에서 4곳을 개별 항목으로 나열 |
| 브라우저가 `cancel` 없이 `<dialog>`를 닫아 굳음 버그 재도입 | 네이티브 `close` 이벤트를 권위 있는 신호로 삼아 `phase`를 내리고 `onClose()`로 부모 `isOpen`을 동기화. 중복 호출은 가드(Decision 2 함정 4, `tasks.md` 1.5·1.6) |
| 강제 닫힘으로 연결 중 모달이 사라져 늦게 온 성공이 방으로 튕겨 넣음 | `RoomsView.handlePasswordSubmit`에 세대 토큰 가드 + `client.deactivate()`(Decision 5a, `tasks.md` 4.8) |
| 방 생성 중 모달이 닫힌 뒤 도착한 `createRoom` 성공이 **취소한 방으로** 튕겨 넣음 | `dismissible={!isCreating}` + `RoomCreate` 내부 세대 가드로 `onCreated` 미호출. 방은 서버에 남으므로 목록을 갱신해 노출(Decision 5b, `tasks.md` 4.9·4.10, 수동 검증 7.2c) |
| 배경 클릭 판정 오판(패딩 클릭이 배경으로) | `<dialog>`에 패딩·배경 없음, 안쪽 래퍼가 시각 담당(Decision 2) |
| StrictMode 이중 이펙트로 `showModal()` 예외 | `if (!dialog.open)` / `if (dialog.open)` 가드(Decision 2) |
| reduced-motion 대응이 굳음 버그 재도입 | `animation-duration: 0.01ms` + 타임아웃 폴백(Decision 3) |
| 재오픈 시 이전 입력 잔존 | children 서브트리는 `phase === "closed"`에서 언마운트되므로 재오픈 시 새로 마운트(내부 키는 방어적 보강). state를 `Modal` 위에 둔 소비자(`RoomCreate`·`PasswordModal`)는 항상 마운트된 채 값을 들고 있어 소비자가 리셋 — 책임 경계를 Decision 4 표로 명시하고 `tasks.md` 4.7에 항목화 |
| 곡 추가 입력 유실 | 이번엔 수용, 더티 확인은 후속. 구조상 `onClose`만 바꾸면 되도록 남겨둠(Decision 4) |
| 모달 안 `absolute z-10` 요소가 top-layer에서 깨짐 | 내부 스택 컨텍스트라 영향 없음. 수동 검증 항목으로 확인(`Dropdown.tsx:50`, `RoomCreate.tsx:208`) |
| 모달 **밖** 전역 토스트가 top-layer 배경 아래로 내려가 실패 피드백이 사라짐(`RoomCreate.tsx:320`) | `<Toaster />`를 popover로 top layer에 올리고 모달이 열릴 때마다 재승격(Decision 8, `tasks.md` 1.13·5절). 수동 검증으로 확인(`tasks.md` 7.12) |
| Popover 미지원 브라우저에서 `showPopover()` 부재로 전역 `<Toaster />` 마운트가 예외를 던져 화면 전체가 깨짐 | `typeof el.showPopover === "function"` feature detection으로 감싸고 미지원 시 종전 일반 레이어 렌더로 폴백(Decision 8, `tasks.md` 5.1) |
| React `autoFocus`가 `<dialog>`에서 무효라 파괴적 버튼·iframe에 초기 포커스 | `initialFocusRef`로 `showModal()` 직후 명령형 `focus()`(Decision 2 함정 3·Decision 6, `tasks.md` 1.12). 뒤로가기 모달·곡 편집 레이어의 초기 포커스를 수동 검증(`tasks.md` 7.4a) |
| 자동 테스트 부재 | 프론트에 테스트 프레임워크가 없다. `npm run typecheck`·`npm run build` + 수동 시나리오(`tasks.md` 7절)로 검증 |

## 마이그레이션

한 PR에서 `Modal`과 소비자 4곳, 그리고 전역 `<Toaster />`(Decision 8)를 함께 바꾼다. 저장소 안의 모든 호출부를 동시에 고치므로 중간 상태가 없고, 프론트 단독 변경이라 `git revert` 한 번으로 완전 롤백된다. 백엔드·인프라 배포와 순서 의존이 없다.
