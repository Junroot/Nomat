## Why

선행 변경 `add-game-round-engine`(아카이브됨)는 **서버 주도 라운드 엔진과 실시간 프로토콜**까지만 만들고, 방 화면의 오디오 재생·라운드 UI·재접속 화면 복원은 명시적으로 **후속 프론트 변경**으로 미뤄뒀다. 본 변경이 그 후속이다.

지금 `PLAYING`은 프론트에서 빈 우산이다 — `RoomView.tsx:156`은 `status === "PLAYING"`이면 **채팅을 통째로 숨기고** "곧 라운드가 시작됩니다" 플레이스홀더 한 줄을 띄운다. 그런데 이 게임에서 **정답 입력이 곧 채팅**이므로(서버가 `/app/rooms/chat`을 정답으로 판정), 채팅을 숨기는 현재 화면은 게임을 할 수 없는 화면이다. 방 안에서 트랙을 재생하는 코드도, 남은 시간·점수판·정답 공개를 그리는 코드도 없다.

서버는 이미 필요한 것을 전부 내려주고 있다: 단일 STOMP 구독 `/topic/rooms/{roomId}`로 `ROUND_STARTED`(answer-stripped 재생 참조)·`ROUND_REVEALED`(정답·승자·점수판)를 방송하고, 재접속 복원 스냅샷은 기존 `GET /rooms/{roomId}` 응답의 `RoomDetailResponse.round`에 실어 준다. 본 변경은 **새 API·새 백엔드 없이** 이 프로토콜을 화면으로 반영한다.

핵심은 **프론트가 라운드 시계의 주인이 아니라는 점**이다 — 서버가 라운드 시작·마감·공개 시각을 소유하고 sweeper로 전이를 구동한다. 그래서 프론트는 라운드를 스스로 끝내거나 정답을 판정하지 않는다. 서버 이벤트를 받아 클립을 재생하고, 서버가 준 `deadlineAt`까지 **표시용** 카운트다운을 돌리며, 다음 이벤트를 기다리는 **서버 종속 단말**이다. 로컬 시계는 화면 표시에만 쓰고 전이 판정에는 절대 쓰지 않는다.

비자명한 결정 여섯 가지가 이 설계를 끌고 간다 (각각 `design.md`에서 상술):

- **라운드 상태는 `useReducer` 슬라이스로 분리하되, 단일 STOMP 구독은 `useRoomSubscription`이 계속 소유한다.** 라운드는 서로 얽힌 7개 필드(phase·roundSeq·totalRounds·deadlineAt·currentTrack·title·winnerId·scores)와 명확한 전이(idle→OPEN→REVEAL→…→results)를 가진 상태 머신이라 `front/CLAUDE.md`가 강제하는 "3개 이상이면 useReducer"에 정확히 해당한다. 다만 StrictMode 이중 마운트·자발 퇴장 타임아웃 등 미묘한 구독 생명주기 로직(`useRoomSubscription.ts:173-211`)은 절대 건드리지 않고, 순수 리듀서를 별도 모듈로 빼 그 구독 핸들러가 라운드 이벤트를 `dispatch`하게 한다(Decision 1).
- **오디오 자동재생은 사용자 제스처 게이트로 무장한다.** 브라우저는 사용자 제스처 없는 오디오 자동재생을 차단하는데, 방장이 아닌 참가자는 게임 시작 시점에 아무것도 클릭하지 않는다. 그래서 `PLAYING` 진입(및 재접속) 시 "게임 참여 / 소리 켜기" 1회 제스처 오버레이로 플레이어를 arming하고, 이후 라운드는 조용히 자동재생한다. 노래 맞히기라 muted 자동재생은 무의미하므로 제스처가 필수다(Decision 2).
- **정답은 이미 채팅에 노출되므로 프론트는 숨기지 않는다.** 실제 배포 코드(`RoomStompController.chat`)는 정답 여부와 무관하게 **채팅 원문을 항상 먼저 방송**한 뒤 정답을 판정한다(아카이브된 백엔드 스펙의 "정답은 방송 안 함"과 어긋나며, 소스 오브 트루스는 코드다). 따라서 우승 추측은 추측 피드에 그대로 뜨고, 뒤이어 도착하는 `ROUND_REVEALED`가 **정규 정답(`title`)·승자·점수판**을 확정 공개한다. 프론트는 정답 은닉을 시도하지 않고 REVEAL의 `title`을 정규 표기로 쓴다(Decision 3).
- **최종 점수는 마지막 `ROUND_REVEALED`에서 sticky로 보존한다.** `ENDED`(`GameEndedEventMessage`)에는 점수판이 실리지 않는다 — 최종 점수는 직전 `ROUND_REVEALED`가 전달했다. 그래서 리듀서는 마지막으로 받은 `scores`를 `ENDED`를 가로질러 유지하고, 그 값으로 결과(순위) 화면을 그린다(Decision 4).
- **재접속·새로고침은 `fetchRoomDetail().round` 스냅샷으로 리듀서를 하이드레이트한다.** 서버 스냅샷은 phase로 게이팅돼 `OPEN` 중엔 정답을 빼고 온다. 프론트는 이 스냅샷으로 클립·phase·카운트다운·점수판을 그대로 복원하되, 오디오는 제스처 게이트를 거쳐 재개한다(Decision 5).
- **재생이 거부되는 트랙은 고칠 수 없으므로 감지해 알리는 데서 멈춘다.** 일부 YouTube 트랙은 임베드에서 재생이 거부된다(실측: `VyvhvlYvRnc`가 `CONTENT_CHECK_REQUIRED`). 임베드에는 인-플레이스 동의 경로가 없어(유일한 버튼이 youtube.com 새 창 링크) **프론트가 재생시킬 방법이 존재하지 않는다**. 게다가 이때 `onError`가 오지 않을 수 있어 현재 구현은 아무것도 눈치채지 못한 채 무음 라운드가 흘러간다 — 사용자는 자기 오디오를 의심하게 된다. 그래서 재생 개시를 관찰해 실패를 감지하고 "이 곡이 재생되지 않는다"를 알린다. 라운드는 서버 권위이므로 프론트가 스킵·종료하지 않는다. 실제 해결(대체 업로드로 트랙 교체)은 기존 플레이리스트 편집 화면의 몫이다(Decision 6).

## What Changes

### front/ (`room` 화면 — 라운드 UI 신규)

**프로토콜 타입 (`app/utils/`)**

- `RoomDetailResponse.ts`: `round?: RoundSnapshotResponse` 필드 추가(하위호환) — `phase`·`roundSeq`·`totalRounds`·`deadlineAt`·`currentTrack`(embedId·startTimeSec·endTimeSec·repeatCount)·`title`(REVEAL/ENDED에서만)·`winnerId`·`scores`(`{playerId, score}[]`). 재접속 복원용
- 신규 `RoundEvent.ts`(또는 `useRoomSubscription`의 유니온 확장): `ROUND_STARTED`·`ROUND_REVEALED` 이벤트 타입. 두 이벤트는 행위자가 없어 `playerId`/`nickname`이 null

**라운드 상태 머신 (`app/hooks/` — 순수 리듀서 신규)**

- 신규 `roundReducer.ts`(또는 `useRoundState`): `RoundState`(위 7필드 + `armed`/`revealTitle` 등 파생 없이 서버 필드 그대로) + 순수 `roundReducer(state, action)`. 액션: `ROUND_STARTED`·`ROUND_REVEALED`·`GAME_STARTED`·`GAME_ENDED`·`HYDRATE`·`RESET`. **`Date.now()`를 리듀서에 넣지 않는다**(순수성 — 카운트다운은 리듀서 밖 표시 로직). 마지막 `scores`는 `GAME_ENDED`를 가로질러 sticky 유지
- `useRoomSubscription.ts`: 단일 구독은 그대로 두고, `handleEvent`에 `ROUND_STARTED`/`ROUND_REVEALED` 분기를 추가해 위 리듀서로 `dispatch`. 최초 `fetchRoomDetail` 응답의 `round`가 있으면 `HYDRATE` dispatch(재접속 복원). 훅 반환에 `round` 상태 추가

**게임용 오디오 플레이어 (`app/components/ui/` — 신규)**

- 신규 `RoundAudioPlayer.tsx`(또는 `MusicPlayer` 확장): 컨트롤 없는 hidden iframe(기존 `MusicPlayer`의 `hidden` 래퍼 패턴 재사용). `ROUND_STARTED`의 `currentTrack`을 **자동재생**하고, YouTube `ended`(state 0) 시 `seekTo(startTimeSec)`로 **`repeatCount`회 반복** 후 정지. `roundSeq`를 key로 트랙 교체 시 리마운트. 재생 소진/오차와 무관하게 라운드 종료는 서버 REVEAL이 결정(재생과 라운드 상태 분리)

**재생 상태 탐지 (`app/hooks/` — 신규)**

- 신규 탐지 훅(`useClipPlayback` 등): 재생 상태(`idle`/`playing`/`blocked`/`unplayable`)를 반환. `onError`는 즉시 판정, 자동재생 정책 차단(`onAutoplayBlocked`)은 재생 불가와 구분, **state 3(BUFFERING) 관측 시 판정 해제**(느린 네트워크 오탐 방지), 그 외 3초 경과 시 재생 불가. 탐지 로직을 `RoundAudioPlayer`에 인라인하지 않는 이유는 `front/CLAUDE.md`의 로직-훅 분리 원칙과, 상위 컴포넌트가 안내를 렌더해야 하기 때문

**PLAYING 화면 재구성 (`app/routes/RoomView.tsx`)**

- `status === "PLAYING"`의 플레이스홀더(현 156-161행)를 라운드 화면으로 교체: 라운드 헤더(`roundSeq / totalRounds`) + **서버 `deadlineAt` 기준 표시용 카운트다운**(0 도달 시 "판정 중…", 로컬 시계로 전이하지 않음) + 점수판(`scores`를 `players`와 조인해 닉네임 표기) + `REVEAL` 정답 오버레이(정규 `title`·승자 하이라이트) + hidden `RoundAudioPlayer`
- **채팅 입력·피드를 게임 중에도 노출**(정답 추측 채널). 별도 정답 입력 UI를 만들지 않는다 — 기존 채팅 입력(`sendMessage`) 재사용
- 최초 `PLAYING` 진입·재접속 시 **오디오 제스처 게이트** 오버레이(기존 `isDeactivated` 오버레이 패턴 재사용) — 클릭으로 플레이어 arming
- 게임 종료(`GAME_ENDED`) 시 **최종 결과(순위) 오버레이** — sticky `scores`를 `players`와 조인해 순위 표시, 우승자 하이라이트, "방으로" 닫기. 종료 후 `status`는 이미 `ACTIVE`
- **재생 불가 안내** — 탐지 훅이 `unplayable`을 반환하면 "이 곡이 재생되지 않는다"를 명시해(사용자가 자기 오디오를 의심하지 않도록) 라운드가 계속됨과 함께 표시. `blocked`은 재생 불가가 아니라 소리 켜기 재유도로 구분

**시스템 메시지 (`app/utils/ChatMessage.ts`)**

- 필요 시 `SystemMessage.eventType`에 라운드 관련 항목(예: 라운드 시작/공개 안내)을 추가할지는 design에서 결정 — 최소안은 기존 start/end만 유지하고 라운드는 전용 UI로만 표현(채팅 피드는 추측만)

### back/ — 변경 없음

라운드 엔진·프로토콜·스냅샷은 `add-game-round-engine`에서 이미 완성됐다. 본 변경은 기존 STOMP 이벤트·`RoomDetailResponse.round`·`/app/rooms/chat`을 **소비만** 한다. 신규 엔드포인트·이벤트·필드를 서버에 요구하지 않는다.

### infra/ — 변경 없음

## Capabilities

### New Capabilities

- `room-round-ui`: 서버 주도 라운드 엔진의 실시간 프로토콜을 방 화면에 반영하는 프론트 능력 — `ROUND_STARTED` 수신 시 클립 자동재생·표시용 카운트다운, 채팅 기반 정답 추측, `ROUND_REVEALED` 수신 시 정답·승자·점수판 표시, 서버 주도 종료 시 최종 결과 화면, 재접속 스냅샷 복원, 오디오 자동재생 제스처 게이트, **클립 재생 실패 감지·안내**. 백엔드 라운드 행위(`room-game-session`)와 분리된 **클라이언트 렌더링 관심사**라 별도 capability로 둔다.

### Modified Capabilities

- 없음 (기존 `room-game-session`은 서버 행위 스펙이라 손대지 않는다 — 프론트는 그 프로토콜의 소비자)

## Impact

- **서브프로젝트**: `front/`만. `back/`·`infra/` 영향 없음
- **도메인 모듈**: 프론트 `room` 화면(`RoomView`·`useRoomSubscription`·라운드 리듀서·`RoundAudioPlayer`). 백엔드 도메인 모듈 무변경
- **헥사고날 계층**: 해당 없음(프론트 전용 변경)
- **DB 스키마 / ES 매핑 / Kafka 토픽**: 영향 없음
- **Redis 키/채널**: 영향 없음. 프론트는 STOMP `/topic/rooms/{id}`(기존 구독)와 REST `GET /rooms/{roomId}`(기존)만 소비
- **API 계약 변화**: **없음**. 소비하는 계약은 모두 `add-game-round-engine`에서 확정됨 — 신규 STOMP 이벤트(`ROUND_STARTED`·`ROUND_REVEALED`), `RoomDetailResponse.round` 스냅샷, `/app/rooms/chat`의 정답 판정 의미. 본 변경은 이를 화면에 반영만 한다
- **외부 의존성**: 기존 `react-youtube`(YouTube IFrame API) 재사용 — 신규 npm 의존성 없음
- **동작 변화**:
  - `PLAYING` 화면이 플레이스홀더에서 실제 게임 화면(클립 재생·카운트다운·점수판·추측 채팅·정답 공개)으로 바뀐다
  - 게임 중 채팅이 다시 보이고, 그것이 정답 추측 채널로 동작한다
  - 게임 종료 시 결과(순위) 화면이 뜨고 방이 `ACTIVE` 로비로 복귀한다
  - 재접속/새로고침 시 진행 중 라운드가 스냅샷으로 복원된다(정답은 OPEN 중 비노출)
  - 재생이 거부되는 트랙에서 무음으로 방치되지 않고 재생 불가 안내가 표시된다(라운드는 그대로 진행)
- **알려진 트레이드오프/리스크**(design에서 상술):
  - **오디오 자동재생**: 제스처 게이트로 완화(비-방장 무음 라운드 예방)
  - **clock skew**: `deadlineAt`은 서버 권위, 프론트 카운트다운은 표시용이라 시계 오차는 화면 표시만 어긋나고 라운드 정확성은 무관. 서버가 `serverNow`를 함께 주면 de-skew 가능하나 범위 외
  - **repeatCount 재생**: 기존 `MusicPlayer`는 단발 재생이라 반복 로직을 새 플레이어로 구현
  - **id-only 점수판**: `scores`는 닉네임이 없어 `players`와 조인, 퇴장자는 "(퇴장)"으로 폴백
  - **재생 거부 트랙**: 프론트가 재생시킬 방법이 없어 감지·안내까지만 한다. 해당 라운드는 곡 없이 흘러가며, 실제 해결은 플레이리스트에서 대체 업로드로 교체하는 것
- **롤백**: 프론트 단일 PR `git revert`. 서버·인프라 변경이 없어 코드 원복으로 완전 롤백
- **범위 외(후속 과제)**:
  - 클라이언트 간 재생 시각 동기화("버퍼 완료 후 동시 시작") — 백엔드의 버퍼 ack 핸드셰이크 확장에 종속되므로 본 변경 밖
  - 정답 추측의 오답/근접 피드백, 실시간 타이핑 표시 등 게임성 강화 UX
  - 재생 실패의 서버 신고·라운드 무효화/재추첨·트랙 블랙리스트 — 본 변경은 클라이언트 감지·안내까지만 한다(Decision 6). 탐지 훅이 상태를 반환하는 구조라 후속 change에서 호출부만 확장하면 된다
  - 플레이리스트 편집 화면(`MusicPlayer` 미리듣기)의 재생 실패 안내 — 같은 탐지 훅을 재사용할 수 있으나 본 변경의 영향 범위(`room` 화면) 밖이라 후속 change로 둔다
