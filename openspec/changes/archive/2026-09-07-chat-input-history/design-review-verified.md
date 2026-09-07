# 검증 사실 캐시

이 루프 실행 중 실제 코드를 열어 확인한 관찰 사실. 코드는 루프 중 불변이므로
같은 루프의 후속 에이전트는 이 관찰을 직접 확인한 것과 동등하게 신뢰해도 된다.
사실만 담는다 — 심각도·지적·권고·평가 금지.

- `front/app/components/ui/ChatInput.tsx:27-29` — `ChatInput`은 `useState("")`로 `input`을 소유하고 `inputRef`를 `<input>`에 건다. props는 `placeholder`, `onSend`, `passRoundSeq`, `onPass` 넷뿐 (라운드 1)
- `front/app/components/ui/ChatInput.tsx:31-40` — `window.addEventListener("keydown", ...)` 전역 리스너는 `e.key === "Enter" && document.activeElement !== inputRef.current`일 때만 `preventDefault` + `focus()`. 다른 키는 보지 않음 (라운드 1)
- `front/app/components/ui/ChatInput.tsx:42-47` — `handleSend`: `input.trim()`이 비면 조기 반환, 아니면 `onSend(trimmed)` 후 `setInput("")` (라운드 1)
- `front/app/components/ui/ChatInput.tsx:57-70` — `onChange={(e) => setInput(e.target.value)}`, `maxLength={200}`. `onKeyDown`은 `e.key === "Enter" && !e.nativeEvent.isComposing`일 때만 분기: `preventDefault` 후 `shiftKey`면 `passRoundSeq !== null`일 때 `onPass(passRoundSeq)`, 아니면 `handleSend()`. `ArrowUp`/`ArrowDown` 분기 없음 (라운드 1)
- `front/app/components/ui/ChatInput.tsx:72-76` — 전송 버튼 `disabled={!input.trim()}`, `onClick={handleSend}` (라운드 1)
- `front/app/routes/RoomView.tsx:62-70` — `isLoading || !roomDetail`이면 스피너만 렌더(이 분기에서는 `ChatInput` 미렌더) (라운드 1)
- `front/app/routes/RoomView.tsx:80, 117` — `isMobile`은 `Column1` 조건부 렌더와 모바일 토글 버튼에만 쓰임. `ChatInput`(188-193)은 `Column2` 안에 조건 없이 한 번 렌더되고 `key` 없음. `placeholder`는 `isPlaying`, `passRoundSeq`는 `round.phase === "OPEN" ? round.roundSeq : null`로 넘김 (라운드 1)
- `front/app/hooks/useRoomSubscription.ts:90-95, 272, 283` — `roomDetail`은 `useState(null)` 후 `setRoomDetail(detail)` 1회, `isLoading`은 `useState(true)` 후 `.finally(() => setIsLoading(false))` 1회. 파일 내 다른 `setIsLoading`/`setRoomDetail` 호출 없음(grep 전수) (라운드 1)
- `front/app/hooks/useRoomSubscription.ts:59, 128` — `MAX_CHAT_MESSAGES = 300`. 메시지 추가 시 `prev.length >= MAX_CHAT_MESSAGES`면 앞을 `slice`로 잘라냄 (라운드 1)
- `front/app/hooks/useRoomSubscription.ts:213-217` — `sendMessageRef.current`는 `!client?.connected`면 아무것도 하지 않고 반환, 아니면 `/app/rooms/chat`으로 `{ content }` publish (라운드 1)
- `front/app/hooks/useRoomSubscription.ts:301, 304` — `sendMessage`·`pass`는 `useCallback(..., [])`로 ref를 경유하는 고정 참조 (라운드 1)
- `front/app/hooks/useBreakpoint.ts:15-17` — `isMobile`/`isTablet`/`isDesktop`은 `window.matchMedia(...).matches`로 계산 (라운드 1)
- `front/app/utils/ColorSlotAllocator.ts:1-25` — `app/utils/`에 위치한 React 의존 없는 `export default class ColorSlotAllocator`. 클래스 doc 주석에 "규칙(design.md Decision 2):" 형식으로 규칙 요약 (라운드 1)
- `front/app/stores/VolumeStore.ts:38-41, 99` — `sanitize(persisted)` 함수가 localStorage 값을 정화하고 `persist`의 `merge`에서 호출됨 (라운드 1)
- `front/app/components/ui/VolumeControl.tsx:28-38` — `document` `keydown` 리스너는 `open`일 때만 등록되고 `e.key === "Escape"`만 처리 (라운드 1)
- `front/app` 전체 grep `ArrowUp|ArrowDown|keydown|onKeyDown` — 방향키를 보는 곳은 `TimePicker.tsx`(자기 `<input>`의 `onKeyDown`)뿐. `window`/`document` 수준 keydown 리스너는 `ChatInput.tsx:38`(Enter)과 `VolumeControl.tsx:37`(Escape) 둘 (라운드 1)
- `front/node_modules/react-dom/package.json:3` — react-dom `19.0.0` (라운드 1)
- `front/node_modules/react-dom/cjs/react-dom-client.development.js:1561, 1578-1584` — `updateInput`은 `type !== "number"`일 때 `element.value !== "" + getToStringValue(value)`인 경우에만 `element.value = ...` 대입 (라운드 1)
- `front/package.json:16-17` — `react`·`react-dom` `^19.0.0` (라운드 1)
