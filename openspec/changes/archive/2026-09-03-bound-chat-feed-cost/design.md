## Context

`RoomView`(291줄)가 방 화면의 모든 상태를 들고 있다 — 방 정보, 채팅 입력값, 메시지 목록, 라운드 상태, 오디오 플레이어. 이 중 가장 자주 바뀌는 것이 채팅 입력값인데, 그것이 루트에 있어 **가장 잦은 변경이 가장 넓은 리렌더를 일으킨다.**

```
 지금                                       바꾼 뒤
 RoomView [input, messages, round…]         RoomView [messages, round…]
   ├ RoundPanel        ← 키 입력마다 렌더      ├ RoundPanel         ← 키 입력에는 렌더 안 됨 (새 메시지·라운드 이벤트에는 여전히 렌더)
   ├ RoundAudioPlayer  ← 키 입력마다 렌더      ├ RoundAudioPlayer   ← 키 입력에는 렌더 안 됨 (새 메시지·라운드 이벤트에는 여전히 렌더)
   ├ messages.map ×N   ← 키 입력마다 N개       ├ ChatMessageList (memo) ← 새 메시지에만, 새 항목 1개
   └ <input>                                  └ ChatInput [input]  ← 키 입력은 여기서 끝
```

메시지 배열은 `useRoomSubscription`에서 `setMessages(prev => [...prev, msg])`로만 자란다. 상한이 없고 `key={index}`로 그려진다.

`messages`는 바꾼 뒤에도 `RoomView`에 남는다(`useRoomSubscription`이 소유하고 `RoomView`가 구조분해한다). 따라서 새 메시지가 올 때 `RoundPanel`·`RoundAudioPlayer`가 함께 렌더되는 것은 이 change 뒤에도 그대로다 — 이 change가 없애는 것은 **키 입력에 의한** 리렌더다. 새 메시지에 의한 두 컴포넌트의 리렌더는 비용이 메시지 수와 무관하게 일정하고(라운드 상태와 두 개의 콜백 묶음만 다시 만든다) 관측된 지연의 원인이 아니므로 Non-Goals에 둔다.

## Goals / Non-Goals

**Goals**
- 키 입력 처리 비용을 메시지 수와 무관하게 만든다
- 피드가 점유하는 DOM·메모리에 상한을 둔다
- 채팅의 보이는 동작(바닥 추종, 스크롤 예외, 서식)을 바꾸지 않는다
- `RoomView`를 분리 기준(200줄) 안쪽으로 되돌린다

**Non-Goals**
- 목록 가상화(react-window 등) — 상한 300개면 DOM이 수천 노드 수준으로 묶여 가상화가 필요 없다. 의존성과 스크롤 복잡도를 들이지 않는다
- 채팅 이력의 서버 보존이나 재접속 시 복원 — 서버 계약 변경이며 별개의 change다
- 메시지 항목의 시각 디자인 변경 — inline SVG 아이콘도 그대로 둔다(Decision 5)
- `RoundPanel`·`RoundAudioPlayer`의 메모이제이션 — 새 메시지마다 렌더되지만 비용이 메시지 수와 무관하게 일정하다(Context 참조). 필요해지면 별도 change
- YouTube 플레이어 메모리 — `recycle-idle-clip-player`

## Decision 1 — 입력 상태를 `ChatInput`으로 내린다 (메모이제이션으로 우회하지 않는다)

근본 원인은 상태의 위치다. `RoomView`에 `input`을 남긴 채 주변을 전부 `React.memo`·`useCallback`으로 감싸면 동작은 하지만, 앞으로 `RoomView`에 무엇을 더할 때마다 같은 방어를 반복해야 한다. 상태를 그것을 쓰는 곳으로 내리면 방어가 필요 없다.

`ChatInput`은 `input` 상태·`inputRef`·전송 버튼을 소유하고 `onSend(content: string)`만 받는다. `onSend`는 `useRoomSubscription`의 `sendMessage`이며 이미 `useCallback`으로 참조가 고정돼 있어 `ChatInput`이 불필요하게 렌더되지 않는다.

Enter를 누르면 입력창으로 포커스를 옮기는 전역 `keydown` 리스너도 함께 옮긴다. 이 리스너는 `inputRef`에만 의존하므로 입력창을 소유하는 컴포넌트가 들고 있는 것이 맞다.

## Decision 2 — 메시지 목록은 `ChatMessageList` + `ChatMessageItem` 두 단계로 메모이제이션한다

`ChatMessageList`는 `messages`만 받고 `React.memo`로 감싼다. 메시지가 오면 배열 참조가 바뀌므로 목록 컴포넌트는 렌더되지만, 그 아래 `ChatMessageItem`도 `React.memo`이고 메시지 객체는 한 번 만들어진 뒤 바뀌지 않으므로 **기존 항목은 props 동일성으로 건너뛰고 새 항목만 마운트된다.**

메시지 객체가 불변이어야 이 전제가 선다. `useRoomSubscription`은 이미 새 객체를 push만 하고 기존 객체를 수정하지 않는다 — 이 성질을 주석으로 못 박는다.

`useStickToBottom`도 `ChatMessageList`가 호출한다. 스크롤 컨테이너 ref와 `onScroll`이 이 컴포넌트 안에 있으므로 훅이 여기 있어야 한다. `RoomView`는 스크롤에 대해 아무것도 모르게 된다.

## Decision 3 — 상한은 300개, 절단은 `useRoomSubscription`의 단일 지점에서 한다

`setMessages`를 호출하는 지점이 다섯 곳(`JOINED`·`LEFT`·`CHAT`·`STARTED`·`ENDED`)이다. 각각에서 절단을 반복하지 않고 `appendMessage(msg)` 헬퍼 하나로 모은다:

```ts
const MAX_CHAT_MESSAGES = 300;
function appendMessage(prev, msg) {
    const next = prev.length >= MAX_CHAT_MESSAGES ? prev.slice(prev.length - MAX_CHAT_MESSAGES + 1) : prev;
    return [...next, msg];
}
```

**왜 300인가.** 화면에는 15~20개가 보인다. 300개면 한 라운드에 추측이 20개씩 나와도 15라운드 전까지 되짚을 수 있어 "아까 누가 뭐라 했지"를 충분히 감당한다. 항목 하나가 DOM 노드 약 12개(inline SVG 포함)이므로 3,600노드 안팎 — 가상화 없이 무난한 크기다. 상수 하나로 조정하되, 스펙이 "현재 N = 300"으로 값을 들고 있으므로 조정 시 스펙과 이 Decision을 같은 커밋에서 갱신한다(태스크 5.2a).

**대안 — 상한 없이 메모이제이션만**: 키 입력 지연은 잡히지만 새 메시지마다 `[...prev, msg]` 복사와 `scrollIntoView` 레이아웃이 N에 비례해 계속 자란다. 상한이 있어야 두 번째 축이 닫힌다.

## Decision 4 — 메시지 식별자는 클라이언트가 부여하는 단조 증가 정수다

서버 이벤트에는 메시지 id가 없다. `timestamp`는 같은 밀리초에 두 메시지가 올 수 있어 키로 부적합하고, 시스템 메시지의 `timestamp`는 클라이언트가 `new Date()`로 만든다.

`useRoomSubscription`이 `nextMessageIdRef`를 들고 `appendMessage`에서 `id: nextMessageIdRef.current++`를 붙인다. `ChatMessage.ts`의 `ChatMessageBase`에 `id: number`가 추가된다. 목록은 `key={msg.id}`를 쓴다.

상한 절단으로 배열 앞이 사라져도 남은 항목의 id는 그대로이므로 React가 항목을 재사용한다. `key={index}`를 유지하면 절단 한 번에 300개 항목 전부가 "다른 항목"으로 취급되어 전부 다시 마운트된다 — 메모이제이션이 무의미해진다.

## Decision 5 — 항목의 inline SVG는 그대로 둔다

메시지마다 `UsersIcon`을 인라인으로 넣는 것이 리컨실 비용의 한 축이었지만, 메모이제이션으로 기존 항목이 렌더되지 않고 상한으로 항목 수가 묶이면 이 비용은 사라진다. `<svg><use>` 스프라이트로 바꾸는 것은 svgr 설정을 건드리는 별도 작업이고 이 change의 목표에 필요하지 않다.

## Decision 6 — 절단 시 스크롤 위치는 브라우저 scroll anchoring에 맡긴다

위로 스크롤해 과거를 읽는 참가자의 화면에서 목록 앞쪽이 잘려 나가면 콘텐츠가 위로 당겨질 수 있다. Chrome·Firefox의 scroll anchoring(`overflow-anchor: auto`, 기본값)은 **뷰포트 위쪽 콘텐츠의 삽입·제거를 보정**하므로 읽고 있던 메시지가 제자리에 남는다. `useStickToBottom`은 바닥 근처일 때만 개입하므로 서로 충돌하지 않는다.

Safari는 scroll anchoring을 지원하지 않아 절단 순간 위로 스크롤 중인 화면이 항목 하나 높이만큼 뛸 수 있다. 상한 절단은 한 번에 한 항목씩 일어나고, 그 참가자는 바닥을 보고 있지 않은 소수이므로 수용한다. 스펙도 이 차이를 그대로 담는다 — scroll anchoring 지원 브라우저에서는 "뛰지 않아야 한다", 미지원 브라우저에서는 "항목 하나 높이 이내"가 수용 기준이다. 두 경우 모두 바닥으로 끌려가는 것은 허용하지 않는다.

`scrollTop`을 직접 보정하는 방식은 이 change에서 채택하지 않는다. Chrome·Firefox에서는 브라우저 보정과 겹쳐 두 배로 밀리므로 `overflow-anchor: none`으로 브라우저 보정을 꺼야 하고, 그러면 잘 되는 브라우저의 동작을 우리 코드로 대체하는 셈이 된다. Safari에서 항목 하나 높이의 점프가 실제로 불편으로 보고되면 그때 별도 change로 다룬다.

## 위험과 완화

| 위험 | 완화 |
|---|---|
| `ChatMessageItem`이 메모돼 있는데 메시지 객체를 어디선가 변경해 화면이 갱신되지 않음 | `useRoomSubscription`이 메시지를 불변으로 다룬다는 주석. 현재 코드에 변경 지점은 없다 |
| `RoomView`에서 `onSend`가 매 렌더 새 함수가 되어 `ChatInput`이 계속 렌더됨 | `sendMessage`는 이미 `useCallback([])`. `RoomView`가 래핑하지 않고 그대로 넘긴다 |
| 상한 300이 너무 작아 "아까 대화가 사라졌다"는 불만 | 상수 하나. 실측 후 조정 |
| Safari에서 절단 순간 스크롤 점프 | Decision 6. 소수·경미하며 필요 시 보정 코드 추가 |
| `useStickToBottom` 이동으로 `REVEAL` 시 영역 축소 보정(`ResizeObserver`)이 깨짐 | 컨테이너 ref와 함께 통째로 옮기므로 동작은 같다. 태스크 5.3에서 회귀 확인 |

## 마이그레이션

데이터·서버 변경은 없다. 프론트 배포만으로 적용되며, 되돌리려면 이전 프론트 빌드를 다시 배포한다.

**배포 순서** — `recycle-idle-clip-player` **이후에** 배포한다. 기능상 의존은 없고 어느 순서로 배포해도 동작한다. 순서를 두는 이유는 실측 분리다: 메모리 원인(플레이어)과 입력 지연 원인(채팅)을 각각 따로 확인하려면 한 번에 하나씩 바꿔야 한다(태스크 5.8). 두 change를 함께 배포해야 하는 사정이 생기면 이 제약은 포기해도 된다.
