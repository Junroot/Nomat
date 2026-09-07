## Context

방 화면의 채팅 입력창(`app/components/ui/ChatInput.tsx`)은 입력값 상태를 스스로 소유한다. 키 입력이 화면 루트(`RoomView`)를 다시 렌더시키지 않기 위한 결정이며, 부모는 참조가 고정된 `onSend`·`onPass`로 전송 시점에만 관여한다. 키 처리는 이미 `onKeyDown` 한 곳에 모여 있다 — `Enter`는 전송, `Shift+Enter`는 포기, 둘 다 IME 조합 중(`isComposing`)에는 발화하지 않는다.

```
 RoomView ──(onSend: 고정 참조)──▶ ChatInput
                                     ├─ useState(input)          ← 키 입력은 여기서 끝난다
                                     ├─ onKeyDown: Enter / Shift+Enter (+ isComposing 가드)
                                     └─ 전역 Enter → 입력창 포커스
```

히스토리를 붙일 자리는 이 경계 안이다. 입력값과 같은 컴포넌트가 이력을 들면 부모·형제(메시지 피드, 라운드 패널, 오디오 플레이어)는 이 기능의 존재를 모른다.

**제약**

- 프론트에 테스트 프레임워크가 없다. 자동 검증은 `typecheck`·`build`뿐이다
- 단일 행 `<input>`이다. 브라우저는 `ArrowUp`/`ArrowDown`에 "캐럿을 맨 앞/뒤로" 정도의 기본 동작만 갖고 있어 가로채도 잃는 것이 없다
- 한국어 IME. 조합 중 방향키를 가로채 값을 갈아끼우면 조합 중이던 글자가 깨진다

## Goals / Non-Goals

**Goals:**

- 데스크톱에서 `↑`/`↓`로 자신이 보낸 메시지를 시간 역순으로 불러온다. 셸의 감각과 일치해야 한다 — 설명 없이 써도 예상대로 움직여야 한다
- 탐색 규칙(draft 보존·복원, 연속 중복 접기, 상한)을 React와 무관한 **순수 모델**로 분리해 나중에 테스트를 붙일 수 있게 한다
- 기존 키 동작(`Enter`, `Shift+Enter`, 전역 Enter 포커스, IME 가드)을 조금도 바꾸지 않는다
- 부모(`RoomView`) 렌더를 유발하지 않는다

**Non-Goals:**

- 새로고침·재접속·방 재입장에 걸친 이력 영속(`sessionStorage` 등). 방 세션 동안만이다
- bash처럼 이력 항목을 제자리에서 편집·보존하는 동작. 탐색 중 편집은 탐색을 끝낸다
- 이력 검색(`Ctrl+R`), 자동완성, 탐색 중임을 알리는 UI 힌트
- 모바일 대응. 방향키가 없으므로 자연히 적용되지 않으며 대체 UI를 만들지 않는다
- 서버 측 변경. 채팅 전송 계약(`/app/rooms/chat`)은 그대로다

## Decision 1 — 이력은 `ChatInput`이 소유하고, 서버 에코 `messages`에서 파생하지 않는다

이력의 원천은 **`onSend`에 넘긴 문자열**이다. 전송 직후 `handleSend`에서 모델에 밀어 넣는다.

`useRoomSubscription`의 `messages`를 `senderId === meId`로 걸러 쓰는 대안은 기각한다.

- `messages`는 피드 상한으로 앞이 잘린다. 이력이 피드 길이에 종속되면 안 된다
- 서버 왕복을 거친 뒤에야 이력에 들어가므로, 전송 직후 `↑`를 누르면 아직 없을 수 있다
- `ChatInput`이 `messages`와 `meId`를 받게 되어, 새 메시지마다 입력창이 렌더된다 — 입력 상태를 이 컴포넌트로 내린 이유를 정면으로 거스른다

## Decision 2 — 순수 모델 `InputHistory` + `useRef` 연결

`app/utils/InputHistory.ts`에 React 의존 없는 클래스를 둔다. `ColorSlotAllocator`와 같은 자리·같은 성격이다 — 규칙이 있는 상태 기계이고, 컴포넌트에 인라인하면 `useState`·`useRef`가 네 개(entries, cursor, draft, 상한) 늘어나며 규칙이 키 핸들러 안에 흩어진다.

```
 InputHistory
 ├─ entries: string[]        가장 오래된 것 → 최신
 ├─ cursor: number           entries.length 이면 "탐색 중 아님"(draft 자리)
 ├─ draft: string            탐색을 시작한 순간의 미전송 텍스트
 │
 ├─ push(content)            연속 중복 접기 → 상한 초과분 앞에서 제거 → cursor = length, draft = ""
 ├─ prev(current) → string?  탐색 시작이면 draft = current. cursor==0 이면 null(변화 없음). 아니면 cursor-1 항목
 ├─ next() → string?         탐색 중 아니면 null. cursor+1. 끝에 닿으면 draft, 아니면 그 항목
 └─ exitBrowsing()           cursor = length. (draft는 다음 prev가 다시 잡는다)
```

```
 entries  [ h0  h1  h2 ]          cursor = 3 (draft 자리)

 "abc" 타이핑 ─ ↑ ──▶ draft="abc", cursor=2, 표시 h2
                ↑ ──▶ cursor=1, 표시 h1
                ↑ ──▶ cursor=0, 표시 h0
                ↑ ──▶ (null) 그대로 h0
                ↓ ──▶ cursor=1, 표시 h1
                ↓ ──▶ cursor=2, 표시 h2
                ↓ ──▶ cursor=3, 표시 "abc"   ← draft 복원
                ↓ ──▶ (null) 그대로
```

컴포넌트는 `useRef(new InputHistory())`가 아니라 **지연 초기화**로 든다 — `useRef`의 초기값 식은 매 렌더 평가되므로, 첫 렌더에만 생성하도록 `ref.current ??= new InputHistory()` 형태를 쓴다. 모델은 렌더를 유발하지 않는다. 화면에 보이는 것은 `input` 상태뿐이고, `↑`/`↓`는 모델의 반환값으로 `setInput`을 한 번 호출할 뿐이다.

`ChatInput`이 커스텀 훅(`useInputHistory`)을 두는 대안은 이 규모에서는 계층만 하나 더 생긴다. 모델이 이미 순수하고, 컴포넌트 쪽 접착은 키 분기 두 줄과 `onChange`의 `exitBrowsing()` 한 줄이다. 두 번째 사용처가 생기면 그때 훅으로 감싼다.

## Decision 3 — 탐색 중 타이핑은 탐색을 끝내고, 이력 항목은 불변이다

`onChange`에서 `exitBrowsing()`을 부른다. `↑`로 불러온 텍스트를 한 글자라도 고치면 커서가 draft 자리로 돌아가고, 다음 `↑`는 **가장 최근 항목**부터 다시 시작하며 그 순간의 편집 텍스트가 새 draft가 된다.

bash는 이력 항목을 제자리에서 고치고 그 편집을 세션 동안 기억한다. 그 동작을 채택하지 않는 이유:

- 이 입력창의 핵심 용례는 "직전 추측 불러와 → 살짝 고쳐 → 전송"이다. 고친 것은 바로 전송되고, 전송되면 그 자체가 새 항목이 된다. 편집본을 슬롯에 남겨 둘 필요가 없다
- 항목이 불변이면 `↑`를 몇 번 눌러도 "내가 보낸 것"만 나온다. 편집 중이던 잔해가 이력에 섞이는 상황이 생기지 않는다
- 모델이 단순해진다. `entries`를 쓰는 곳이 `push` 하나다

`onChange`는 IME 조합 중에도 글자마다 발화하지만 `exitBrowsing()`은 정수 하나를 대입할 뿐이라 비용이 없고, 조합 중이라는 것은 이미 사용자가 타이핑을 시작했다는 뜻이므로 탐색을 끝내는 것이 맞다. `setInput`으로 값을 갈아끼우는 프로그램적 변경은 React `onChange`를 발화시키지 않으므로, `↑`/`↓` 자체가 탐색을 끝내는 일은 없다.

**감수하는 손실**: "abc" 타이핑 → `↑` → 불러온 항목 편집 → `↑`를 누르면, 처음의 "abc"는 사라진다(편집본이 draft를 덮는다). 탐색 중 편집이 곧 "이걸 쓰겠다"는 신호라고 보고 받아들인다.

## Decision 4 — 연속 중복만 접고, 상한은 50이다

`push`는 **직전 항목과 같은 내용**일 때만 무시한다(셸 `HISTCONTROL=ignoredups`). 떨어진 중복은 그대로 둔다.

- 같은 추측을 연타하거나 "ㅋㅋ"를 세 번 보냈을 때 `↑` 세 번이 같은 것을 보여 주면 이력이 쓸모없어진다
- 전체 중복 제거(`erasedups`)는 시간 순서를 흐트러뜨린다. "A, B, A"를 보낸 사용자가 `↑↑`로 B를 기대하는데 `erasedups`면 A가 앞으로 옮겨져 B가 첫 `↑`에 나온다

상한 50은 라운드 수(플레이리스트 크기)와 한 라운드의 추측 횟수를 곱해도 넉넉하고, 200자 × 50이라 메모리는 논외다. 넘치면 가장 오래된 것부터 버린다. 잘려 나가는 동안 탐색 중이면 커서가 한 칸씩 어긋날 수 있으나, `push`는 전송 시점에만 일어나고 전송은 커서를 끝으로 되돌리므로 그 상황은 생기지 않는다.

## Decision 5 — 키 처리: IME 가드, `preventDefault`, 캐럿은 명세에 맡긴다

`onKeyDown`에 다음 분기를 더한다.

```
 ArrowUp   && !isComposing → preventDefault; r = history.prev(input);  r !== null → setInput(r)
 ArrowDown && !isComposing → preventDefault; r = history.next();       r !== null → setInput(r)
```

- **IME 가드**는 `Enter`와 같은 `e.nativeEvent.isComposing`을 쓴다. 조합 중에는 브라우저 기본 동작에 맡긴다(IME가 방향키를 후보 이동에 쓰는 경우가 있다)
- **`preventDefault`는 반환값과 무관하게** 건다. 이력이 비었거나 끝에 닿아 `null`이 와도 캐럿이 맨 앞/뒤로 튀지 않게 한다. 단일 행 입력에서 `↑`/`↓`가 캐럿 이동 수단인 사용자는 없다고 본다
- **캐럿 위치**: 값이 프로그램적으로 바뀌면 캐럿이 텍스트 끝으로 가는 것은 HTML 명세가 보장한다(`value` setter — "move the text entry cursor position to the end of the text control"). React 제어 컴포넌트는 값이 달라졌을 때만 `node.value`에 대입하므로 이 경로를 그대로 탄다. 따라서 `setSelectionRange`를 따로 부르지 않는다. 수동 검증에서 어긋나는 브라우저가 관측되면 그때 `useLayoutEffect` + 플래그로 못 박는다
- `null`이면 `setInput`을 부르지 않는다. 같은 값으로 `setInput`해도 React가 렌더를 건너뛰지만, 의도를 코드에 남기기 위해서다

## Decision 6 — 방 세션 동안만 산다

`ChatInput`이 언마운트되면 이력도 사라진다. 방 퇴장·화면 이탈이 그 경계다. 새로고침(재접속 유예로 실제 일어난다)도 이력을 잃는다.

영속을 붙이지 않는 이유는 비용 대비 가치가 낮아서다. `sessionStorage`에 roomId 키로 두면 되지만, 그러면 `ChatInput`이 roomId를 알아야 하고, 저장값 정화(`VolumeStore`가 하는 것과 같은 일)가 붙고, 방을 옮겼을 때 지우는 규칙이 필요하다. 새로고침 뒤 직전 추측을 다시 치는 비용은 몇 초이고 드물다. 필요가 확인되면 모델은 그대로 두고 `entries`의 적재·저장만 붙이면 된다 — 그래서 모델과 저장을 지금 섞지 않는다.

## 위험과 완화

- **[IME 조합 중 `isComposing`이 false인 브라우저·입력기]** → `Enter` 경로가 이미 같은 가드로 운영 중이고(#237 계열 이슈 해결), 이 change는 그 판정을 재사용한다. 새 위험을 더하지 않는다
- **[캐럿이 끝으로 가지 않는 환경]** → 명세상 보장되지만 수동 검증 항목에 넣는다. 관측되면 Decision 5의 폴백(`useLayoutEffect` + 플래그)을 적용한다
- **[`↑`로 불러온 텍스트를 실수로 `Enter`해 오답·잡담을 재전송]** → 셸과 같은 동작이며 사용자가 기대하는 바다. 불러온 것은 편집 없이 전송해도 무해한 자기 메시지다
- **[전역 Enter 포커스 리스너와의 상호작용]** → 리스너는 `Enter`만 보므로 방향키에 반응하지 않는다. 입력창에 포커스가 없을 때 `↑`는 아무 일도 하지 않으며, 페이지 스크롤 같은 기본 동작은 그대로다(가로채지 않는다)
- **[`Shift+↑` 등 조합키]** → 셸에서도 의미가 없다. 조합키 여부를 보지 않고 동일하게 처리한다. 단일 행 입력에서 `Shift+↑`의 기본 동작(맨 앞까지 선택)을 잃지만 쓰는 사용자가 없다고 본다

## 마이그레이션

프론트 전용 변경이며 저장 형식·API 계약이 없다. 배포는 Netlify 프로덕션 배포 그대로이고, 롤백은 이전 빌드로 되돌리면 끝난다. 방향키를 쓰지 않는 사용자에게는 동작 차이가 없다.
