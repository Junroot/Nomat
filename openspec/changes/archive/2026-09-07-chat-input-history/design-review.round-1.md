# design-review — chat-input-history (라운드 1)

검증 통과 지적 없음. `design.md`·`proposal.md`·`tasks.md`·`specs/chat-input-history/spec.md`를 교차 검토하고, 설계의 코드 주장은 `ChatInput.tsx`·`RoomView.tsx`·`useRoomSubscription.ts`·`ColorSlotAllocator.ts`·`VolumeStore.ts`·react-dom 19.0.0 소스를 직접 열어 확인했다. `[치명]`·`[높음]`으로 세울 수 있는 결함은 없었다.

## 확인한 코드 주장 (전부 일치)

| 설계 주장 | 앵커 | 결과 |
|---|---|---|
| `ChatInput`이 입력값 상태를 소유, `onKeyDown` 한 곳에 `Enter`/`Shift+Enter` + `isComposing` 가드 | `ChatInput.tsx:28, 59-70` | 일치 |
| 전역 Enter 리스너는 `Enter`만 본다 | `ChatInput.tsx:31-40` | 일치 |
| `handleSend`는 `trim()` 빈 값 조기 반환 → `onSend(trimmed)` → `setInput("")` | `ChatInput.tsx:42-47` | 일치. `push`를 `onSend` 뒤에 끼우는 자리가 그대로 있다 |
| 부모의 `onSend`·`onPass`는 참조가 고정 | `useRoomSubscription.ts:301, 304` | 일치 (`useCallback(..., [])` + ref 경유) |
| `messages`는 피드 상한으로 앞이 잘린다 | `useRoomSubscription.ts:59, 128` | 일치 (`MAX_CHAT_MESSAGES = 300`) |
| `ColorSlotAllocator`와 같은 자리·성격 | `ColorSlotAllocator.ts:1-25` | 일치 (`app/utils/`, React 의존 없는 클래스, doc 주석에 Decision 인용) |
| `VolumeStore`가 저장값을 정화한다 | `VolumeStore.ts:41, 99` | 일치 |
| React 제어 컴포넌트는 값이 달라졌을 때만 `node.value`에 대입 | `react-dom-client.development.js:1582-1584` (19.0.0) | 일치 |

## 모델 규칙 추적

Decision 2의 `push`/`prev`/`next`/`exitBrowsing` 정의로 spec의 시나리오 전부를 손으로 돌렸다 — 기본 탐색, 끝에서의 no-op, draft 보존·복원, 빈 draft 복원, 탐색 중 편집 후 재시작·새 draft, 편집이 항목을 바꾸지 않음, 연속 중복 접기, 떨어진 중복 보존, 51번째 전송, 불러와 고쳐 전송, 전송 후 draft 소거. 모두 spec의 THEN과 일치한다. `tasks.md` 1.1-1.5의 서술도 Decision 2와 어긋나는 곳이 없다.

## 기각한 후보

- **라운드 전이·게임 시작/종료·재접속 복원 때 `ChatInput`이 언마운트되어 이력이 사라지는가** — 반증. `ChatInput`은 `Column2` 안에 조건 없이 한 번 렌더되고 `key`가 없다(`RoomView.tsx:188-193`). 스피너 분기(`RoomView.tsx:62`)를 가르는 `isLoading`·`roomDetail`은 마운트 후 각각 한 번만 바뀌고 되돌아가지 않는다(`useRoomSubscription.ts:90-95, 272, 283`). `isMobile`은 `Column1`과 토글 버튼에만 걸린다(`RoomView.tsx:80, 117`). 따라서 spec의 "게임 시작·라운드 전이·게임 종료는 이력을 비우지 않는다"는 구조상 성립한다.
- **다른 전역 `keydown` 리스너가 `↑`/`↓`에 반응해 이력 탐색과 충돌하는가** — 반증. `front/app` 전수 grep 결과 `window`/`document` 리스너는 `ChatInput.tsx:38`(Enter)과 `VolumeControl.tsx:37`(Escape, 열려 있을 때만) 둘뿐이고, `TimePicker.tsx`의 방향키 처리는 자기 `<input>`의 `onKeyDown`이라 방 화면과 무관하다.
- **STOMP 미연결 상태의 전송이 이력에 들어가 spec의 "전송에 성공적으로 넘긴"과 어긋나는가** — 반증. `sendMessage`는 `!client?.connected`면 조용히 반환한다(`useRoomSubscription.ts:214-217`). 그러나 spec의 "전송 직후(서버 방송 전) 바로 불러올 수 있다" 시나리오와 Decision 1이 이력의 원천을 **`onSend`에 넘긴 문자열**로 못 박고 있어, "넘긴"은 콜백 인계를 뜻한다. 미연결 시 이력에 남는 것은 오히려 재전송을 돕고, 설계의 다른 결정을 무너뜨리지 않는다.
- **Decision 4의 "잘려 나가는 동안 탐색 중이면 커서가 어긋난다"가 실제로 생기는가** — 반증. `push`는 `handleSend` 경로(`Enter`·전송 버튼)에서만 불리고 그 안에서 `cursor = length`로 되돌린다. 탐색 상태와 `push`가 겹치는 경로가 없다.
- **`isComposing`이 `false`인 조합 중 방향키가 조합 글자를 깨뜨리는가** — 설계가 "위험과 완화"에서 `Enter` 경로와 같은 판정을 재사용한다고 명시했고, 그 판정은 이미 운영 중이다(`ChatInput.tsx:60-62`). 새 위험이 아니며, 수용 근거를 뒤집을 반례를 세우지 못했다.

판정: 진입 가능 — 코드 주장 8건이 전부 현재 코드와 일치하고, 모델 규칙이 spec 시나리오 전부를 만족하며, 문서 간 불일치와 누락된 부수 효과 경로(프론트 전용, 서버·저장소 영향 없음)가 없다.
