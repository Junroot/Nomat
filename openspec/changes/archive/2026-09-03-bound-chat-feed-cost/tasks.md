## 1. 프론트엔드 — 메시지 상한과 식별자 (`front/`)

- [x] 1.1 `app/utils/ChatMessage.ts` — `ChatMessageBase`에 `id: number`를 추가한다
- [x] 1.2 `app/hooks/useRoomSubscription.ts` — 상수 `MAX_CHAT_MESSAGES = 300`과 `nextMessageIdRef`를 추가하고, id 부여·상한 절단을 함께 하는 `appendMessage` 헬퍼를 만든다(design.md Decision 3·4). 왜 300인지 주석으로 남긴다
- [x] 1.3 `setMessages(prev => [...prev, {...}])`를 호출하는 다섯 곳(`JOINED`·`LEFT`·`CHAT`·`STARTED`·`ENDED`)을 모두 `appendMessage`로 바꾼다. 절단 로직이 헬퍼 밖에 남지 않도록 한다
- [x] 1.4 메시지 객체는 생성 후 변경하지 않는다는 불변 전제를 훅 주석에 명시한다(항목 메모이제이션의 근거 — design.md Decision 2)

## 2. 프론트엔드 — `ChatInput` 분리 (`front/`)

- [x] 2.1 `app/components/ui/ChatInput.tsx` 신규. `RoomView`의 `input` 상태·`inputRef`·`handleSend`·입력창·전송 버튼(SVG 포함)을 옮긴다. props는 `onSend: (content: string) => void` 하나다(design.md Decision 1)
- [x] 2.2 Enter로 입력창에 포커스하는 전역 `keydown` 리스너(`useEffect`)를 `ChatInput`으로 옮긴다
- [x] 2.3 `maxLength={200}`, IME 조합 중 Enter 무시(`e.nativeEvent.isComposing`), 빈 입력 시 버튼 비활성 등 기존 동작을 그대로 유지한다
- [x] 2.4 `app/routes/RoomView.tsx` — `<ChatInput onSend={sendMessage} />`로 교체한다. `sendMessage`를 새 함수로 감싸지 않는다(참조 고정이 깨진다)

## 3. 프론트엔드 — `ChatMessageList` 분리와 메모이제이션 (`front/`)

- [x] 3.1 `app/components/ui/ChatMessageList.tsx` 신규. `messages: RoomChatMessage[]`를 받아 스크롤 컨테이너와 항목을 그린다. `React.memo`로 감싼다
- [x] 3.2 같은 파일에 `ChatMessageItem`(`React.memo`)을 두고 시스템 메시지·일반 메시지 분기를 옮긴다. `key`는 `msg.id`다 — **`index`를 쓰지 않는다**(design.md Decision 4)
- [x] 3.3 `nicknameColor`·`formatTime`·`SYSTEM_MESSAGE_TEXT`·`NEON_COLORS`를 `RoomView`에서 이 파일로 옮긴다
- [x] 3.4 `useStickToBottom(messages)` 호출과 `containerRef`·`endRef`·`onScroll` 배선을 `ChatMessageList` 안으로 옮긴다. `REVEAL` 시 영역 축소 보정을 담당하는 `ResizeObserver` 동작은 훅 안에 있으므로 함께 따라온다
- [x] 3.5 `app/routes/RoomView.tsx` — `<ChatMessageList messages={messages} />`로 교체한다. 옮긴 뒤 `RoomView`가 200줄 안쪽인지 확인하고, 남은 것이 방 정보·플레이어 목록·라운드 배선만인지 본다

## 4. 프론트엔드 — 빌드 게이트 및 실측 (`front/`)

- [x] 4.1 `npm run typecheck` 통과
- [x] 4.2 `npm run build` 통과
- [ ] 4.3 **리렌더 실측** — React DevTools Profiler에서 "Record why each component rendered"를 켜고 입력창에 글자를 친다. 커밋에 `ChatInput`만 나타나야 한다. `RoomView`·`ChatMessageList`·`RoundPanel`·`RoundAudioPlayer`가 보이면 상태 배치나 참조 고정이 어긋난 것이다
- [ ] 4.4 **항목 실측** — 새 메시지가 도착할 때 Profiler 커밋에 `ChatMessageItem`이 하나만(새 항목) 나타나는지 확인한다

## 5. 수동 검증

프론트에 테스트 프레임워크가 없다. 2인 이상으로 실제 게임을 진행해 확인한다. 상한 동작을 눈으로 잡기 위해 검증 중에는 `MAX_CHAT_MESSAGES`를 임시로 10 정도로 낮춰도 된다(커밋 전 복원).

- [ ] 5.1 **주 수정** — 수백 개의 채팅을 쌓은 뒤 입력창에 빠르게 타이핑해 지연이 방 입장 직후와 다르지 않은지 확인
- [ ] 5.2 **상한** — 상한을 넘기면 가장 오래된 메시지가 위에서 사라지고 개수가 상한에 머무는지 확인. 시스템 메시지도 함께 세어지는지 확인
- [ ] 5.2a **상한 값 조정 시 문서 동기화** — 실측 결과 `MAX_CHAT_MESSAGES`를 300에서 바꾸면 `specs/room-round-ui/spec.md`의 "현재 N = 300"과 design.md Decision 3의 값을 같은 커밋에서 함께 갱신한다
- [ ] 5.3 **바닥 추종 회귀** — 바닥을 보고 있을 때 새 메시지·정답 공개(영역 축소)·다음 라운드(영역 확대) 모두에서 최신 메시지가 보이는지 확인(`useStickToBottom` 이동 회귀)
- [ ] 5.4 **절단 중 스크롤 예외** — 위로 스크롤해 과거를 읽는 중에 상한 절단이 일어나도 읽던 메시지가 제자리에 있고 바닥으로 끌려가지 않는지 확인. Chrome과 Safari 양쪽에서 본다. Chrome은 뛰지 않아야 하고, Safari는 항목 하나 높이 이내로만 움직여야 한다(design.md Decision 6). 그 이상 움직이면 절단 로직이 한 번에 여러 항목을 지우고 있는 것이다
- [ ] 5.5 **입력 동작 회귀** — Enter 전송, 한글/일본어 IME 조합 중 Enter 무시, 200자 제한, 빈 입력 시 버튼 비활성, 채팅 영역 밖에서 Enter로 입력창 포커스가 모두 그대로인지 확인
- [ ] 5.6 **정답 추측 회귀** — 게임 중 채팅으로 정답을 맞혔을 때 승자 표시·점수 갱신이 이전과 같은지 확인(전송 경로가 바뀌지 않았다는 확인)
- [ ] 5.7 **모바일** — "방 정보 · 플레이어" 토글과 가상 키보드 등장 시 채팅 영역이 깨지지 않는지 확인
- [ ] 5.8 **장시간 세션** — `recycle-idle-clip-player` 배포 후 100곡 이상 진행해 키 입력 지연이 남지 않는지 확인. 두 change의 효과를 분리해 보기 위해 이 change는 그 뒤에 배포한다
