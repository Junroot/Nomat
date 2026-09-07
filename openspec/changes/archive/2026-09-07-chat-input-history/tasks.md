## 1. 프론트엔드 — 순수 모델 `InputHistory` (`front/`)

React 의존 없이 `app/utils/InputHistory.ts`에 둔다. `ColorSlotAllocator.ts`와 같은 자리·같은 문체(클래스 상단 doc 주석에 규칙 요약, design.md Decision 번호 인용)로 쓴다.

- [x] 1.1 `app/utils/InputHistory.ts` — `entries: string[]`, `cursor: number`(`entries.length`면 탐색 중 아님), `draft: string`을 가진 클래스를 만든다. 상한은 생성자 인자(기본 50)로 받는다
- [x] 1.2 `push(content)` — 직전 항목과 같으면 무시(연속 중복 접기, design.md Decision 4), 아니면 끝에 추가하고 상한 초과분을 앞에서 제거한다. 어느 경우든 `cursor = entries.length`, `draft = ""`로 되돌린다
- [x] 1.3 `prev(current)` — 탐색 중이 아니면(`cursor === entries.length`) `draft = current`로 보관한다. `cursor === 0`이거나 이력이 비었으면 `null`. 아니면 `cursor`를 1 줄이고 그 항목을 돌려준다
- [x] 1.4 `next()` — 탐색 중이 아니면 `null`. `cursor`를 1 올리고, `entries.length`에 닿으면 `draft`, 아니면 그 항목을 돌려준다
- [x] 1.5 `exitBrowsing()` — `cursor = entries.length`. draft는 지우지 않는다(다음 `prev`가 다시 잡는다, design.md Decision 3)
- [x] 1.6 클래스 doc 주석에 상태 전이 도식(design.md Decision 2의 `entries / cursor / draft` 그림)과 "왜 연속 중복만 접는가", "왜 항목이 불변인가"를 한 줄씩 남긴다

## 2. 프론트엔드 — `ChatInput` 연결 (`front/`)

`app/components/ui/ChatInput.tsx`만 고친다. `RoomView.tsx`·`useRoomSubscription.ts`·`ChatMessageList.tsx`는 손대지 않는다(design.md Decision 1). props는 바뀌지 않는다.

- [x] 2.1 `useRef<InputHistory | null>(null)`로 모델을 들고 `ref.current ??= new InputHistory()`로 첫 렌더에만 생성한다. `useState`로 들지 않는다 — 모델 변경이 렌더를 유발하면 안 된다
- [x] 2.2 `handleSend` — `onSend(trimmed)` 뒤 `history.push(trimmed)`를 부른다. `trimmed`가 비어 조기 반환하는 경로는 그대로라 빈 전송은 이력에 들어가지 않는다
- [x] 2.3 `onKeyDown` — `ArrowUp`/`ArrowDown` 분기를 추가한다. `e.nativeEvent.isComposing`이면 아무것도 하지 않고 기본 동작에 맡긴다(기존 `Enter` 가드와 같은 판정). 조합 중이 아니면 반환값과 무관하게 `e.preventDefault()`를 걸고, `prev(input)`/`next()`의 결과가 `null`이 아닐 때만 `setInput`한다(design.md Decision 5)
- [x] 2.4 `onChange` — `setInput` 앞뒤에 `history.exitBrowsing()`을 부른다. `setInput`으로 갈아끼운 값은 `onChange`를 발화시키지 않으므로 `↑`/`↓` 자체가 탐색을 끝내는 일은 없다
- [x] 2.5 컴포넌트 doc 주석에 한 단락을 더한다 — 이력이 여기 사는 이유(입력 상태와 같은 곳, 서버 에코 `messages`에서 파생하지 않는 이유), `preventDefault`를 무조건 거는 이유, 캐럿 끝 위치를 HTML 명세(`value` setter)에 맡기는 이유와 어긋날 때의 폴백(`useLayoutEffect` + 플래그)
- [x] 2.6 `Shift+Enter` 포기 경로가 `push`를 타지 않는지, 전역 Enter 포커스 리스너가 방향키에 반응하지 않는지 코드로 확인한다

## 3. 프론트엔드 — 빌드 게이트 (`front/`)

- [x] 3.1 `npm run typecheck` 통과
- [x] 3.2 `npm run build` 통과

## 4. 수동 검증

프론트에 테스트 프레임워크가 없다. `npm run dev`로 방에 들어가 데스크톱 브라우저(Chrome 필수, 가능하면 Safari·Firefox도)에서 확인한다. 각 항목은 `specs/chat-input-history/spec.md`의 시나리오에 대응한다.

- [x] 4.1 **기본 탐색** — "A", "B", "C"를 보낸 뒤 `↑`×3 → "A"에서 `↑` 한 번 더 → 여전히 "A" → `↓`×3 → 빈 입력 → `↓` 한 번 더 → 여전히 빈 입력. 각 단계에서 전송 버튼 활성 상태가 입력값을 따라가는지 확인
- [x] 4.2 **이력 없음** — 새로 입장해 "ㅁ"을 타이핑하고 `↑`·`↓`를 눌러 값과 캐럿이 그대로인지 확인
- [x] 4.3 **draft 보존·복원** — "A"를 보내고 "치는 중"을 친 뒤 `↑` → "A", `↓` → "치는 중"이 복원되는지 확인
- [x] 4.4 **탐색 중 편집** — "A", "B"를 보내고 `↑`×2로 "A"에서 "A!"로 편집 → `↑` → "B", `↓` → "A!"가 복원되는지, 그리고 전송 없이 `↑`×2를 했을 때 "A"(편집 전)가 나오는지 확인
- [x] 4.5 **연속 중복·떨어진 중복** — "A", "ㅋㅋ", "ㅋㅋ", "ㅋㅋ" 뒤 `↑`×2가 "ㅋㅋ" → "A"인지, "A", "B", "A" 뒤 `↑`×2가 "A" → "B"인지 확인
- [x] 4.6 **전송 후 초기화** — "아이유 좋은날"을 보내고 `↑`로 불러와 "아이유 - 좋은 날"로 고쳐 보낸 뒤 `↑`×2가 "아이유 - 좋은 날" → "아이유 좋은날"인지 확인. 그리고 draft가 있는 상태에서 불러온 것을 그대로 보낸 뒤 `↑`→`↓`의 결과가 빈 입력인지 확인
- [x] 4.7 **IME** — 한글 IME로 "ㄱ"을 조합 중(미확정)인 상태에서 `↑`를 눌러 값이 바뀌지 않고 조합 글자가 깨지지 않는지 확인. macOS 기본 한글 입력기와 Windows MS IME 양쪽이면 좋다
- [x] 4.8 **캐럿 위치** — "아이유 좋은날"을 보내고 `↑` 직후 바로 " (2010)"을 타이핑해 "아이유 좋은날 (2010)"이 되는지 확인. Chrome 외 브라우저에서도 확인하고, 어긋나면 design.md Decision 5의 폴백을 적용한다
- [x] 4.9 **소유 범위** — 다른 참가자의 메시지·시스템 메시지가 `↑`에 나오지 않는지, `Shift+Enter` 포기가 이력에 들어가지 않는지, 전송 직후(서버 방송 전) `↑`가 되는지 확인
- [x] 4.10 **생존 범위** — 라운드 전이·게임 시작·종료를 넘어 이력이 유지되는지, 방 퇴장 후 재입장과 새로고침 뒤에는 비어 있는지, DevTools Application 탭에서 localStorage·sessionStorage에 이력 항목이 없는지 확인
- [x] 4.11 **기존 키 회귀** — `Enter` 전송, `OPEN`에서 `Shift+Enter` 포기, 입력창 밖에서 `Enter` 포커스 이동이 종전과 같은지 확인. 입력창에 포커스가 없을 때 `↑`/`↓`로 페이지 스크롤이 되는지(가로채지 않는지) 확인
- [x] 4.12 **모바일 폭** — 768px 미만에서 입력창 동작에 변화가 없는지 확인(방향키가 없으므로 기능이 드러나지 않아야 정상)
