# design.md 적대적 리뷰

검증 통과 지적 없음. 설계가 인용한 코드 주장을 전부 실제 파일에서 확인했고, 반증을 시도한 여섯 개의 의심은 모두 무너졌다. 아래 `## 기각한 후보` 에 그 근거를 남긴다.

## 기각한 후보

### `winnerNickname` 수명 서술의 자기모순 — 화면에 드러나지 않아 반증됨

Decision 5(`design.md:103`)와 `tasks.md:25` 는 `winnerNickname` 을 "`scores` 와 동일하게 sticky 보존"하면서 동시에 "`winnerId` 와 수명을 맞춘다"고 쓴다. 현재 리듀서에서 이 둘은 실제로 다른 수명이다 — `roundReducer.ts:85-86` 의 `ROUND_STARTED` 는 상태를 전체 교체하며 `winnerId: null` 로 비우고 `scores: state.scores` 는 유지한다. 즉 구현자는 둘 중 하나를 골라야 한다.

그런데 어느 쪽을 골라도 화면 결과가 같다:

- 승자를 그리는 유일한 지점 `RoundRevealOverlay` 는 `RoomView.tsx:70` 의 `showReveal = round.phase === "REVEAL" && !showGate` 로만 마운트된다. `ROUND_STARTED` 직후(`phase === "OPEN"`)에는 렌더되지 않으므로, 그 구간에 `winnerNickname` 이 남아 있든 비워지든 보이지 않는다.
- `ROUND_REVEALED` 는 `roundReducer.ts:94-103` 에서 `winnerId`·`scores` 를 항상 이벤트 값으로 덮어쓴다. 타임아웃 공개(`winnerId=null`)도 마찬가지이므로 이전 라운드 값이 새 라운드로 새는 경로가 없다.
- `RoundResultOverlay.tsx:18-20` 은 우승 하이라이트를 `row.score === topScore` 로 판정하고 `winnerId`/`winnerNickname` 을 아예 읽지 않는다.

설계의 다른 결정이 이 선택에 걸려 있지 않고 구현자가 코드를 보고 국소적으로 정할 수 있으므로, 진입을 막는 결함이 아니다.

### 롤링 배포 중 구버전 인스턴스가 닉네임을 벗겨 중계 — 이미 수용된 degrade와 동일해 반증됨

`infra/app/compose.yml:5,10-11` 은 `replicas: 2` + `parallelism: 1` 이라 배포 중 신·구 인스턴스가 공존한다. 두 인스턴스 모두 `room:*:events` 패턴을 구독하므로(`RoomEventRedisSubscriber.kt:26`), 신버전이 발행한 `ROUND_REVEALED` 를 구버전이 받아 `objectMapper.readValue<RoomEventMessage>` 로 역직렬화한 뒤 `messagingTemplate.convertAndSend(destination, event)` 로 **재직렬화해** 중계한다(`RoomEventRedisSubscriber.kt:32-35`). 구버전 클래스에는 `nickname`·`winnerNickname` 필드가 없으므로(`RoundRevealedEventMessage.kt:10-19`, `RoundSnapshotResponse.kt:45-48`) 그 인스턴스에 붙은 클라이언트에게는 필드가 벗겨진 채 전달된다.

두 갈래로 반증된다:

- **역직렬화 실패로 중계가 끊길 가능성**: 커스텀 `ObjectMapper` 빈이나 `spring.jackson.*` 설정이 저장소에 없다(`src/main/resources`·`infrastructure/` 전체 grep 0건). Spring Boot 기본값은 `FAIL_ON_UNKNOWN_PROPERTIES` 비활성이므로 모르는 필드는 무시되고 중계는 계속된다.
- **닉네임이 벗겨진 채 도달하는 결과**: 이 화면 결과는 `design.md:89` (Decision 4)와 `design.md:110` (Risks·배포 순서)가 이미 명시적으로 수용한 "신버전 프론트가 구버전 백엔드를 만나면 중립 라벨로 degrade" 와 정확히 같고, 배포 창(`parallelism: 1` 순차 교체) 동안으로 한정된다. 새로운 파손 양상이 아니다.

### 점수판 DTO 생성 지점 누락 — 전수 grep으로 반증됨

Decision 2는 점수판 DTO 생성 지점이 두 곳뿐이라고 전제한다. `back/src` 전체 `ScoreEntryResponse` grep 결과 실제 생성부는 `RoundService.kt:121` 과 `RoomEventListener.kt:119` 두 곳뿐이고, 나머지 hit은 import·타입 선언이다. `scoreboard(` 전체 grep도 `RoundService.kt:201` 외에는 어댑터 내부 호출(`RoundStateStoreImpl.kt:89`)과 테스트뿐이다. 세 번째 조인 지점은 없다.

### 프론트에 남는 조인 경로 — 전수 grep으로 반증됨

`front/app` 전체 `(퇴장)` grep은 `scoreboard.ts:10`(주석)·`scoreboard.ts:15`·`RoundRevealOverlay.tsx:26` 3건뿐이고, `joinScores|rankScores` 사용처는 `RoundPanel.tsx:32`·`RoundResultOverlay.tsx:19` 뿐이다. 세 컴포넌트 각각에서 `players` prop의 사용처는 닉네임 조인 한 줄이 전부이며(`RoundPanel.tsx:32`, `RoundRevealOverlay.tsx:26`, `RoundResultOverlay.tsx:19`), `RoomView.tsx:148-150,67` 은 `players` 를 참가자 목록·방장 판정에 계속 쓴다. "prop 자체가 제거된다"(`design.md:40`)와 "`players` 자체는 삭제하지 않는다"(`tasks.md:29`)가 코드와 일치한다.

### 스펙 델타의 MODIFIED 요구사항 헤딩 불일치 — 대조로 반증됨

델타 4건의 `### Requirement:` 제목이 기존 메인 스펙의 제목과 문자 단위로 일치한다: `room-round-ui/spec.md:35, 73`, `room-game-session/spec.md:65, 211`. 기존 시나리오도 전부 보존된 채 새 시나리오만 추가됐다(`room-round-ui` 5→6·2→3, `room-game-session` 재접속 스냅샷 2→3). 델타가 적용되지 못하거나 기존 요구사항을 유실시키는 문제는 없다.

### `AFTER_COMMIT` 리스너에서의 DB 조회 — 기존 코드로 반증됨

`handleRoundRevealed` 는 현재 DB를 건드리지 않는 `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)` 인데(`RoomEventListener.kt:110-126`), 여기에 `@Transactional(readOnly = true)` 인 `PlayerService`(`PlayerService.kt:12-13`) 호출이 들어간다. 같은 클래스의 `handleRoomJoined`·`handleRoomLeft`·`handleGameStarted`·`handleGameEnded` 가 이미 동일한 `AFTER_COMMIT` 문맥에서 `playerService.findById(...)` 를 호출하고 있어(`RoomEventListener.kt:36, 51, 65, 79`), 이 조합이 이 저장소에서 동작한다는 사실은 기존 코드가 증명한다. 또한 `findByIdIn` 은 이미 존재하므로(`PlayerService.kt:40-42`) 설계가 없는 API를 전제하지도 않는다.

판정: 진입 가능 — 설계의 코드 주장(퇴장 시 ZREM, `Player` 삭제 경로 부재, `ADVANCE_ON_CORRECT` 의 조건부 가점 대 무조건 `winnerId` 기록, DTO 생성 지점 2곳, `PlayerService.findByIdIn` 존재, `room`→`player` 기존 의존, 프론트 `players` 사용처)이 모두 실제 코드와 일치하고, 문서 4종(proposal·design·specs·tasks)이 서로 어긋나지 않으며, 제기한 여섯 의심이 전부 반증됐다.
