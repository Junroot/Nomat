## Why

게임을 오래 하면 **채팅 입력창에 글자를 칠 때마다 화면이 버벅인다.** 처음에는 멀쩡하다가 라운드가 쌓일수록 나빠진다. 노래 맞히기에서 채팅은 곧 정답 입력 채널이라 이 지연은 게임 자체를 느리게 만든다.

원인은 `RoomView`의 상태 배치다.

```
 RoomView  ── useState(input) ◀── 키 입력 1회마다 setInput
    │
    ├── messages.map(...)          ← 메시지 전부 다시 렌더 (memo 없음)
    │      └── <UsersIcon/> ×N     ← 메시지마다 inline SVG 통째로
    ├── RoundPanel                 ← 다시 렌더
    ├── RoundAudioPlayer           ← 다시 렌더 → playerHandlers 클로저 재생성 → react-youtube componentDidUpdate
    └── <input value={input}/>     ← 실제로 바뀐 것은 이것 하나
```

1. **입력 상태가 화면 루트에 있다.** 한 글자마다 `RoomView` 전체가 다시 렌더된다
2. **메시지 목록에 메모이제이션이 없다.** `messages.map`이 매 렌더마다 모든 항목을 다시 만든다. 항목마다 `UsersIcon`(766바이트 SVG)이 인라인으로 들어가 리컨실 비용이 크다
3. **메시지 배열에 상한이 없다.** 200곡 동안 추측 채팅이 쌓이면 수천 개가 되고, 키 입력 한 번의 비용이 그 수에 비례한다. 새 메시지마다 `[...prev, msg]`로 배열을 복사하는 비용도 함께 자란다
4. `useStickToBottom`이 새 메시지마다 거대한 목록에서 `scrollIntoView({behavior:"smooth"})`를 호출해 레이아웃을 강제한다

`RoomView`는 이미 291줄로 프로젝트 가이드의 분리 기준(200줄)을 넘겨 있어, 입력·목록을 컴포넌트로 내리는 것은 성능뿐 아니라 구조 정리로도 필요하다.

> 탭 메모리 1GB의 주범은 이쪽이 아닐 가능성이 크다(수천 개 DOM 노드와 fiber는 수십~수백 MB 수준). 메모리는 YouTube 플레이어 재사용이 원인이며 `recycle-idle-clip-player`가 다룬다. 이 change는 **키 입력 지연**을 직접 겨눈다. 두 원인은 독립적이라 change를 나눈다.

## What Changes

### front/ — 입력 상태를 `ChatInput`으로 내린다

- `app/components/ui/ChatInput.tsx` 신규. `input` 상태, `inputRef`, Enter로 입력창에 포커스하는 전역 `keydown` 리스너, 전송 버튼을 소유한다. 부모에는 `onSend(content)`만 노출한다
- 키 입력이 `RoomView`를 다시 렌더하지 않게 된다 — 메시지 목록·라운드 패널·오디오 플레이어가 키 입력과 무관해진다

### front/ — 메시지 목록을 `ChatMessageList`로 내리고 메모이제이션한다

- `app/components/ui/ChatMessageList.tsx` 신규. `messages`를 받아 그리며 `React.memo`로 감싼다. `useStickToBottom`도 이 컴포넌트가 소유한다(스크롤 컨테이너와 같은 곳)
- 항목 컴포넌트 `ChatMessageItem`도 `React.memo`. 새 메시지가 와도 기존 항목은 다시 렌더되지 않고 새 항목만 마운트된다
- 채팅 서식 헬퍼(`nicknameColor`, `formatTime`, `SYSTEM_MESSAGE_TEXT`)가 `RoomView`에서 함께 이동한다

### front/ — 메시지 배열에 상한과 안정적인 id를 둔다

- `useRoomSubscription`이 메시지를 추가할 때 **최근 300개만 유지**한다. 단일 `appendMessage` 헬퍼로 추가·절단을 한곳에서 한다
- 각 메시지에 클라이언트가 부여하는 단조 증가 `id`를 붙인다. 서버 이벤트에는 id가 없고, 상한으로 앞을 잘라내면 배열 인덱스가 밀려 `key={index}`가 무너진다
- `ChatMessage.ts`의 타입에 `id: number`가 추가된다

### back/ · infra/ — 변경 없음

프론트 전용 변경이다. API 계약·DB·ES·Kafka·Redis 영향 없음.

## Capabilities

### New Capabilities

- 없음.

### Modified Capabilities

- `room-round-ui`: 채팅 피드의 비용이 세션 길이에 비례해 자라지 않아야 한다는 요구사항을 추가한다 — 피드 상한, 입력이 피드를 다시 렌더하지 않을 것, 절단 뒤에도 바닥 추종이 유지될 것. 채팅은 로비에서도 쓰이지만 부하가 쌓이는 곳은 게임 중 추측 채팅이고 기존 채팅 요구사항도 이 capability에 있어 여기에 둔다.

## Impact

- **서브프로젝트**: `front/`만. `back/`·`infra/` 영향 없음
- **헥사고날 계층**: 해당 없음(프론트 전용)
- **DB 스키마 / ES 매핑 / Kafka 토픽 / Redis 키**: 영향 없음
- **API 계약 변화**: 없음. 서버 `CHAT`·`JOINED`·`LEFT`·`STARTED`·`ENDED` 이벤트 형태를 그대로 쓴다
- **외부 의존성**: 신규 npm 패키지 없음(가상화 라이브러리를 도입하지 않는다 — design.md Non-Goals)
- **영향 받는 화면**: `/rooms/:roomId`의 채팅 영역. 로비(`ACTIVE`)와 게임 중(`PLAYING`) 모두
- **파일**:
  - 신규 — `app/components/ui/ChatInput.tsx`, `app/components/ui/ChatMessageList.tsx`
  - 수정 — `app/routes/RoomView.tsx`, `app/hooks/useRoomSubscription.ts`, `app/utils/ChatMessage.ts`
- **동작 변화**:
  - 메시지가 수천 개 쌓여도 키 입력 지연이 일정하다 **(주 수정)**
  - 채팅 피드에 최근 300개만 남는다. 그보다 오래된 메시지는 위로 스크롤해도 볼 수 없다
  - 그 외 채팅의 보이는 동작(바닥 추종, 위로 스크롤 중 예외, 시스템 메시지, 닉네임 색)은 그대로다
- **범위 밖**: YouTube 플레이어 메모리(`recycle-idle-clip-player`), 채팅 이력의 서버 보존·재접속 복원, 목록 가상화
- **자동 테스트 불가**: 프론트에 테스트 프레임워크가 없다. 검증은 `npm run typecheck`·`npm run build`와 React DevTools Profiler 실측으로 한다
