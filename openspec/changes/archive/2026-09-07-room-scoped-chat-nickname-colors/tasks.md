## 1. 프론트엔드 — 팔레트 토큰 (`front/`)

- [x] 1.1 `app/app.css`의 `@theme`에 `--color-chat-0` ~ `--color-chat-19`를 design.md Decision 5의 hex 표 그대로 추가한다. 블록 위 주석에 생성 파라미터(OKLCH L 0.77 / C 0.16, 18° 격자, 격자 시작 색상 211.5°, 짝수 격자점 먼저·홀수 격자점 나중 거리순 탐욕 배치, 동률은 색상 값이 작은 쪽)와 "슬롯 번호 = 배정 순서, 앞 슬롯일수록 서로 멀다"를 남겨 조정할 사람이 재현할 수 있게 한다
- [x] 1.2 기존 `--color-neon-*` 토큰은 그대로 둔다(다른 UI가 사용). 채팅 닉네임 외에 `text-neon-*`을 참조하는 곳을 건드리지 않았는지 확인한다

## 2. 프론트엔드 — 슬롯 할당기 (`front/`)

- [x] 2.1 `app/utils/ColorSlotAllocator.ts`를 만든다. 슬롯 수 상수 `CHAT_COLOR_SLOT_COUNT = 20`(방 정원 `MAX_MAX_ENTRIES_COUNT`와 같음을 주석으로 명시)을 export하고, `assign(playerId): number`(이미 있으면 기존 슬롯, 없으면 가장 낮은 빈 슬롯), `release(playerId): void`를 가진 순수 클래스(또는 클로저)를 둔다. React 의존 없음
- [x] 2.2 빈 슬롯이 없을 때의 폴백(`playerId % CHAT_COLOR_SLOT_COUNT`)을 구현하고, 정원 상한이 오르면 팔레트를 함께 늘려야 한다는 주석을 남긴다(design.md Decision 2)
- [x] 2.3 `app/utils/ChatMessage.ts`의 `ChatMessage`에 `colorSlot: number` 필드를 추가한다. 주석에 "발신 시점에 확정되며 이후 바뀌지 않는다(메시지 불변·memo 유지)"를 적는다

## 3. 프론트엔드 — 방 세션 배정 (`front/`)

- [x] 3.1 `app/hooks/useRoomSubscription.ts`에 `useRef(new ColorSlotAllocator())`를 둔다. 훅 인스턴스(= 방 세션)와 수명이 같으며 상태가 아니라 ref인 이유(렌더에 쓰이지 않음, StrictMode 재실행에도 유지)를 주석으로 남긴다
- [x] 3.2 `fetchRoomDetail` 완료 시 `detail.players` 순서대로 `assign`한다. 이미 `CHAT`으로 배정된 참가자는 `assign`이 기존 슬롯을 돌려주므로 별도 분기가 필요 없음을 확인한다
- [x] 3.3 `JOINED` 처리에서 `assign(event.playerId)`를 호출한다. 중복 `JOINED`(이미 목록에 있음)에서도 슬롯이 유지되는지 확인한다
- [x] 3.4 `CHAT` 처리에서 `assign(event.playerId)`로 슬롯을 얻어 `appendMessage`에 `colorSlot`으로 찍는다. 방 상세보다 먼저 온 채팅의 발신자를 위한 경로임을 주석으로 남긴다
- [x] 3.5 `LEFT` 처리에서 본인이 아닌 경우 `release(event.playerId)`를 호출한다. 본인 `LEFT`·`SESSION_REPLACED`는 배정을 건드리지 않는다
- [x] 3.6 `appendMessage`의 "메시지 객체는 절대 변경하지 않는다" 주석에 `colorSlot`도 그 대상임을 한 줄 추가한다

## 4. 프론트엔드 — 피드 렌더 (`front/`)

- [x] 4.1 `app/components/ui/ChatMessageList.tsx`에서 `NEON_COLORS`와 `nicknameColor(senderId)`를 제거하고, `["text-chat-0", …, "text-chat-19"] as const` **리터럴 배열**로 교체한다. 동적 클래스 조립이나 `style` 변수 참조가 Tailwind v4 스캔에 걸리지 않는 이유(design.md Decision 4)를 주석으로 남긴다
- [x] 4.2 배열 길이가 `CHAT_COLOR_SLOT_COUNT`와 같음을 타입 수준(`satisfies` 튜플 길이) 또는 모듈 로드 시 단언으로 보장한다
- [x] 4.3 `ChatMessageItem`이 `msg.colorSlot`으로 클래스를 고르도록 바꾼다. `memo`·`key={msg.id}`·시스템 메시지 렌더는 그대로 둔다

## 5. 프론트엔드 — 빌드 게이트 (`front/`)

- [x] 5.1 `npm run typecheck` 통과
- [x] 5.2 `npm run build` 통과. 빌드 산출 CSS에 `text-chat-0`~`text-chat-19` 클래스와 `--color-chat-*` 변수 20개가 모두 들어 있는지 grep으로 확인한다(스캔 누락 검출)

## 6. 수동 검증

프론트에 테스트 프레임워크가 없다. 브라우저 창(또는 시크릿 창) 여러 개로 서로 다른 Discord 계정으로 한 방에 들어가 확인한다. 3명 이상이 확보되면 6.1~6.4를, 20명은 현실적으로 어려우므로 6.5는 슬롯 순서 확인으로 갈음한다.

- [ ] 6.1 **유일성** — 3명 이상이 각각 채팅을 보냈을 때 모든 닉네임 색이 서로 다른지 확인
- [ ] 6.2 **입장 불변** — 채팅이 오간 뒤 새 사람이 입장했을 때 기존 참가자의 이전·이후 메시지 색이 변하지 않는지 확인
- [ ] 6.3 **퇴장 불변·재사용** — 한 명이 퇴장한 뒤 남은 사람들 색이 그대로인지, 이어서 새로 입장한 사람이 비워진 슬롯(앞 번호)의 색을 받는지 확인
- [ ] 6.4 **먼저 온 채팅** — 새로고침 직후 다른 사람이 즉시 보낸 채팅에 색이 있고, 방 상세가 도착한 뒤에도 그 색이 유지되는지 확인(네트워크 스로틀로 방 상세를 늦추면 재현하기 쉽다)
- [ ] 6.5 **슬롯 순서** — 입장 순서대로 슬롯 0(코랄), 1(청록), 2(황록), 3(라벤더)… 색이 나오는지 확인
- [ ] 6.6 **memo 유지** — React DevTools의 "Highlight updates"를 켜고 새 참가자가 입장할 때 기존 메시지 항목이 하이라이트되지 않는지 확인
- [ ] 6.7 **다른 UI 회귀** — 시작 버튼, 호스트 배지, 볼륨 컨트롤 등 `neon-*`을 쓰는 UI의 색이 변하지 않았는지 확인
