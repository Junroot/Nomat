# Tasks — add-room-game-start

## 1. back/ — 도메인: 상태·전이·가드

- [x] 1.1 `room/application/domain/RoomStatus.kt`: enum에 `PLAYING` 추가 (`PENDING`, `ACTIVE`, `PLAYING`)
- [x] 1.2 `room/application/domain/GameStartedEvent.kt`, `GameEndedEvent.kt` 신규 — `data class`(`roomId: Long`, `playerId: Long`), `application/domain` 패키지에 위치 (직렬화 안정성 규칙)
- [x] 1.3 `room/application/domain/Room.kt`에 `start(playerId)` 추가: `master?.playerId != playerId` → `ForbiddenException`, `status != ACTIVE` → `ConflictException`, 통과 시 `status = PLAYING` + `registerEvent(GameStartedEvent(id, playerId))`
- [x] 1.4 `Room.kt`에 `end(playerId)` 추가: 방장 검증 + `status != PLAYING` → `ConflictException`, 통과 시 `status = ACTIVE` + `registerEvent(GameEndedEvent(id, playerId))`
- [x] 1.5 `Room.join(playerId)` 맨 앞에 가드 추가: `if (status == RoomStatus.PLAYING) throw ConflictException("게임 중에는 입장할 수 없습니다.")` (기존 정원·중복 검사 앞)

## 2. back/ — 서비스·이벤트 전파

- [x] 2.1 `room/application/RoomService.kt`에 `start(roomId, playerId)` 추가 — `distributedLockExecutor.withLock("room:$roomId:lock")` + `writeTransactionTemplate`(`REQUIRES_NEW`)로 감싸 `findById` → `room.start(playerId)` → `roomRepository.save(room)` (기존 `join` 동형)
- [x] 2.2 `RoomService.kt`에 `end(roomId, playerId)` 추가 (2.1과 동형, `room.end` 호출)
- [x] 2.3 `room/application/dto/GameStartedEventMessage.kt`, `GameEndedEventMessage.kt` 신규 — `RoomEventMessage` 구현(`roomId`, `playerId`, `nickname`), `RoomJoinedEventMessage`와 동형
- [x] 2.4 `room/application/dto/RoomEventMessage.kt`의 `@JsonSubTypes`에 `STARTED`(GameStartedEventMessage), `ENDED`(GameEndedEventMessage) 추가
- [x] 2.5 `room/in/RoomEventListener.kt`(`private class` 유지)에 `@TransactionalEventListener(AFTER_COMMIT)` `handleGameStarted`/`handleGameEnded` 추가 — `playerService.findById`로 닉네임 채워 `room:{id}:events` 채널에 발행 (`handleRoomJoined` 동형)

## 3. back/ — 인바운드 컨트롤러

- [x] 3.1 `room/in/RoomStompController.kt`(`private class` 유지)에 `@MessageMapping("/rooms/start")` 추가 — `roomSession()`에서 `roomId`·`playerId` 추출 후 `roomService.start(...)` (`leave` 동형). 세션 없으면 `return`
- [x] 3.2 `RoomStompController.kt`에 `@MessageMapping("/rooms/end")` 추가 (3.1 동형, `roomService.end(...)`)

## 4. back/ — 상태 노출 (DTO)

- [x] 4.1 `room/application/dto/RoomDetailResponse.kt`에 `status: RoomStatus` 필드 추가, `of(...)`에서 `room.status` 매핑

## 5. back/ — 테스트 및 정적 분석

- [x] 5.1 도메인 단위 테스트(`room/application/domain/`): `Room.start` — 방장이 ACTIVE에서 시작 시 PLAYING 전이 + 이벤트 등록 / 비방장 시작 시 `ForbiddenException` / 이미 PLAYING이면 `ConflictException`. `Room.end` 대칭 케이스. `Room.join`이 PLAYING에서 `ConflictException` (기존 `RoomTest` 패턴·네이밍 따름, Spring 컨텍스트 없이 순수 단위)
- [x] 5.2 컨트롤러/통합 테스트: `@IntegrationTest` + WebSocket STOMP 클라이언트로 방장 `/app/rooms/start` 발행 시 `/topic/rooms/{id}`로 `STARTED` 수신, `RoomStep` 픽스처 재사용. 게임 중 신규 플레이어 CONNECT(`join`) 거부, 기존 멤버 재접속 허용 검증 (기존 STOMP 테스트 패턴·Awaitility·Testcontainers 따름 — **새 mock 인프라 도입 금지**, 컨테이너 stop 등 인프라 조작 금지)
- [x] 5.3 통합 테스트: `GET /rooms/{id}` 응답에 `status` 포함, `GET /rooms` 목록이 PLAYING 방을 제외하는지(`ACTIVE`만 노출) 검증
- [x] 5.4 `./gradlew test` 전체 통과
- [x] 5.5 `./gradlew detekt` 통과

## 6. front/ — 게임 시작 UI 연결

- [x] 6.1 `app/utils/RoomDetailResponse.ts`에 `status: "PENDING" | "ACTIVE" | "PLAYING"` 필드 추가
- [x] 6.2 `app/hooks/useRoomSubscription.ts`: `RoomEventMessage` 유니온에 `STARTED`/`ENDED` 타입 추가, `handleEventRef`에 분기(게임 상태 갱신 + 시스템 메시지) 추가
- [x] 6.3 `useRoomSubscription.ts`: 훅 상태에 `status` 추가 — 최초 `fetchRoomDetail` 응답의 `status`로 초기화(재접속/새로고침 복원), 라이브 `STARTED`/`ENDED`로 갱신. 반환에 `status`, `startGame()`, `endGame()`(각각 `client.publish({ destination: "/app/rooms/start" | "/app/rooms/end" })`) 추가
- [x] 6.4 `app/routes/RoomView.tsx`: `시작하기` 버튼(82행) 빈 `onClick`을 `startGame`에 연결, **방장에게만** 노출(현재 사용자 `meId`가 master인지로 판정). `status === "PLAYING"`이면 게임 중 화면(최소 플레이스홀더) 렌더 + 방장에게 "게임 종료"(`endGame`) 액션 제공
- [x] 6.5 `npm run typecheck` 통과
- [x] 6.6 `npm run build` 통과

## 7. 문서

- [x] 7.1 (선택) `back/CLAUDE.md` 또는 `front/CLAUDE.md`에 게임 세션 상태(`PLAYING`)·`/app/rooms/start`·`/app/rooms/end`·`STARTED`/`ENDED` 이벤트 추가 부기 (room 모듈 흐름이 문서화돼 있는 경우)
