# 검증 사실 캐시

이 루프 실행 중 실제 코드를 열어 확인한 관찰 사실. 코드는 루프 중 불변이므로
같은 루프의 후속 에이전트는 이 관찰을 직접 확인한 것과 동등하게 신뢰해도 된다.
사실만 담는다 — 심각도·지적·권고·평가 금지.

## 백엔드 — player 모듈

- `player/application/PlayerService.kt:34-38` — `findById(id: Long): PlayerResponse` 는 `playerRepository.findById(id)` 가 null이면 `NotFoundException(NotFoundResource.PLAYER)` 를 던진다 (라운드 1)
- `player/application/PlayerService.kt:40-42` — `findByIdIn(ids: Set<Long>): List<PlayerResponse>` 가 이미 존재하며 `playerRepository.findByIdIn(ids)` 를 매핑한다 (라운드 1)
- `player/application/PlayerService.kt:12-13` — 클래스에 `@Service`, `@Transactional(readOnly = true)` 가 붙어 있다 (라운드 1)
- `player/application/domain/PlayerRepository.kt:3-12` (파일 전체) — 선언된 메서드는 `existsById`·`findAll`·`findByRegistrationTypeAndRegistrationId`·`findById`·`findByIdIn`·`findByNicknameAndRegistrationType`·`save` 7개. 삭제 메서드 없음 (라운드 1)
- `player/` 패키지 전체 grep `delete|remove` — 일치하는 줄 0건 (라운드 1)
- `player/out/PlayerRepositoryImpl.kt:47-56` — `PlayerJpaRepository` 는 `CrudRepository<Player, Long>` 를 상속하고 커스텀 삭제 메서드를 추가하지 않는다 (라운드 1)

## 백엔드 — room 모듈 라운드 저장소

- `room/out/RoundStateStoreImpl.kt:223-246` (ADVANCE_ON_CORRECT_SCRIPT) — `if redis.call('ZSCORE', KEYS[3], ARGV[3]) then redis.call('ZINCRBY', KEYS[3], 1, ARGV[3]) end` (235-237) 로 가점은 점수판 멤버일 때만 적용되고, 그 뒤 `HSET KEYS[1] ... 'winnerId', ARGV[3]` (240-241) 은 조건 없이 실행된다 (라운드 1)
- `room/out/RoundStateStoreImpl.kt:105-113` — `scoreboard(roomId)` 는 scores ZSET을 `reverseRangeWithScores` 로 읽어 `List<ScoreEntry>` 를 만든다. `removeScore` 는 같은 키에서 `ZREM` (라운드 1)
- `room/out/RoundStateStoreImpl.kt:74-90` — `snapshot()` 은 round Hash에서 읽으며 `winnerId = hash["winnerId"]?.takeIf { it.isNotEmpty() }?.toLong()`, `scores = scoreboard(roomId)` (라운드 1)
- `room/out/RoundRedisKeys.kt:35-44` — 실제 키 스킴은 `round(roomId) = "round:{shard}:$roomId"`, `scores(roomId) = "scores:{shard}:$roomId"`, `deadlinesShard(shard) = "rounds:deadlines:{shard}"` (`shard = roomId mod 64`) (라운드 1)
- `room/application/domain/RoundStateStore.kt:86-89` — `data class ScoreEntry(val playerId: Long, val score: Int)` 는 `room.application.domain` 패키지에 있다 (라운드 1)
- `room/application/domain/RoundStateStore.kt:65-84` — `RoundSnapshot` 에 `winnerId: Long?`, `scores: List<ScoreEntry>` 필드가 있다 (라운드 1)

## 백엔드 — room 모듈 서비스·어댑터

- `room/application/RoundService.kt:94-97` — `onPlayerLeft(roomId, playerId)` 는 `roundStateStore.removeScore(roomId, playerId)` 만 호출한다 (라운드 1)
- `room/application/RoundService.kt:100-124` — `getSnapshot` 이 `RoundSnapshotResponse` 를 만들며 `scores = snapshot.scores.map { ScoreEntryResponse(it.playerId, it.score) }` (121), `winnerId = snapshot.winnerId` (120) (라운드 1)
- `room/application/RoundService.kt:189-207` — `publishRoundRevealed` 가 `RoundRevealedEvent(..., scores = roundStateStore.scoreboard(roomId), ...)` 를 `eventPublisher.publishEvent` 로 발행한다 (라운드 1)
- `room/application/RoundService.kt:34-41` — `RoundService` 생성자 의존은 `RoundStateStore`·`RoomRepository`·`RoomPlaylistTrackRepository`·`ApplicationEventPublisher`·`DistributedLockExecutor`·`PlatformTransactionManager`. 클래스에 `@Transactional` 없음 (라운드 1)
- `room/in/RoomEventListener.kt:26-32` — `@Component private class RoomEventListener` 는 `PlayerService`·`StringRedisTemplate`·`ObjectMapper`·`RoundService` 를 주입받는다 (라운드 1)
- `room/in/RoomEventListener.kt:35-36, 49-51, 64-65, 78-79` — `handleRoomJoined`·`handleRoomLeft`·`handleGameStarted`·`handleGameEnded` 가 모두 `@TransactionalEventListener(AFTER_COMMIT)` 안에서 `playerService.findById(...)` 를 호출한다. `handleRoomLeft` 는 그에 앞서 `roundService.onPlayerLeft(...)` 를 호출한다 (라운드 1)
- `room/in/RoomEventListener.kt:110-126` — `handleRoundRevealed` 는 `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)` 이며 `scores = event.scores.map { ScoreEntryResponse(it.playerId, it.score) }` (119) 로 매핑 후 Redis `convertAndSend` 한다. DB 접근 없음 (라운드 1)
- `room/in/RoomEventRedisSubscriber.kt:29-37` — `@Component private class` 가 `objectMapper.readValue<RoomEventMessage>(message.body)` 로 역직렬화한 뒤 `messagingTemplate.convertAndSend(destination, event)` 로 STOMP에 재직렬화해 중계한다 (라운드 1)
- `room/application/dto/RoomEventMessage.kt:6-16` — `@JsonTypeInfo(use = Id.NAME, property = "type")` + `@JsonSubTypes` 8종. `ROUND_REVEALED` → `RoundRevealedEventMessage` (라운드 1)
- `room/application/dto/RoundSnapshotResponse.kt:14-27` — 현재 필드: `phase`·`roundSeq`·`roundNumber`·`totalRounds`·`deadlineAt`·`currentTrack`·`title`·`winnerId`·`scores`·`nextTrack`. `winnerNickname` 없음 (라운드 1)
- `room/application/dto/RoundSnapshotResponse.kt:45-48` — `data class ScoreEntryResponse(val playerId: Long, val score: Int)` (라운드 1)
- `room/application/dto/RoundRevealedEventMessage.kt:10-19` — 현재 필드: `roomId`·`roundSeq`·`winnerId`·`title`·`scores`·`nextTrack`·`playerId`·`nickname`. `winnerNickname` 없음 (라운드 1)
- `room/application/domain/RoundRevealedEvent.kt:15-22` — 도메인 이벤트 필드는 `roomId`·`roundSeq`·`winnerId`·`title`·`scores: List<ScoreEntry>`·`nextTrack`. 닉네임 필드 없음 (라운드 1)
- `back/src` 전체 grep `ScoreEntryResponse` — 생성(construction) 지점은 `RoundService.kt:121` 과 `RoomEventListener.kt:119` 두 곳뿐. 나머지 hit은 import 2건과 타입 선언·필드 선언 3건 (라운드 1)
- `back/src` 전체 grep `scoreboard(` — 호출부는 `RoundStateStoreImpl.kt:89`, `RoundService.kt:201`, 테스트 2건, 포트 선언 1건 (라운드 1)
- `room/application/RoomService.kt:26-35` — `@Service @Transactional(readOnly = true) class RoomService` 가 `PlayerService` 와 `RoundService` 를 함께 주입받는다 (라운드 1)
- `room/application/RoomService.kt:59-70` — `getDetail` 이 `playerService.findByIdIn(room.playerIds + room.playlistMasterId)` 로 닉네임 맵을 만들고, 같은 메서드에서 `roundService.getSnapshot(roomId)` 를 호출한다 (라운드 1)
- `room/application/RoomService.kt:139, 162` — `roundService.teardownRound(roomId)` 는 방 삭제(`leave` 로 방이 비었을 때)와 방장 수동 종료(`end`)에서만 호출된다 (라운드 1)
- `back/src/main` grep `teardownRound|getSnapshot` — `getSnapshot` 호출부는 `RoomService.kt:67` 한 곳 (라운드 1)
- `back/src/main/resources` 및 `infrastructure/` grep `ObjectMapper|FAIL_ON_UNKNOWN|jackson` — 커스텀 `ObjectMapper` 빈 정의나 `spring.jackson.*` 설정 없음. `application.yml` 에 jackson 블록 없음 (라운드 1)
- `room/application/in/RoomRoundLifecycleIntegrationTest.kt:72` — `assertThat(round.scores.map { it.playerId }).contains(player.id)` (`OPEN 중 재접속 스냅샷에는 정답이 노출되지 않는다` 테스트 안) (라운드 1)

## 프론트엔드

- `front/app/utils/scoreboard.ts:10-23` (파일 전체) — `joinScores(scores, players)` 가 `nicknameById.get(s.playerId) ?? "(퇴장)"` (15) 로 조인하고, `rankScores(scores, players)` 는 `joinScores(...).sort((a,b) => b.score - a.score)` (22) (라운드 1)
- `front/app/utils/RoundEvent.ts:14-18` — `ScoreEntry` 는 `playerId`·`score` 만 가진다. 상단 주석에 "id만 담겨 오므로 화면에서는 방 `players`와 조인" (라운드 1)
- `front/app/utils/RoundEvent.ts:30-43, 62-74` — `RoundSnapshotResponse`·`RoundRevealedEvent` 에 `winnerId` 는 있고 `winnerNickname` 은 없다 (라운드 1)
- `front/app/components/ui/RoundPanel.tsx:30-32` — `players` prop의 유일한 사용처는 `rankScores(round.scores, players)` (32). 파일 전체에서 다른 `players` 참조 없음 (라운드 1)
- `front/app/components/ui/RoundRevealOverlay.tsx:24-26` — `players` prop의 유일한 사용처는 `players.find((p) => p.id === round.winnerId)?.nickname ?? "(퇴장)"` (26). 표시 분기는 `round.winnerId == null` 기준 (라운드 1)
- `front/app/components/ui/RoundResultOverlay.tsx:18-20` — `players` prop의 유일한 사용처는 `rankScores(round.scores, players)` (19). 우승 하이라이트는 `row.score === topScore` 기준이며 `winnerId` 를 쓰지 않는다 (라운드 1)
- `front/app/routes/RoomView.tsx:64-71` — `players` 는 `useRoomSubscription` 에서 받아 `isMaster` 판정(67)에 쓰이고, `showReveal = round.phase === "REVEAL" && !showGate` (70), `showResult = round.phase === "ENDED" && !resultClosed` (71) (라운드 1)
- `front/app/routes/RoomView.tsx:148-150, 187, 266-268` — `players` 는 참가자 수·목록 렌더(148-150)에도 쓰이며, `RoundPanel`(187)·`RoundRevealOverlay`(266)·`RoundResultOverlay`(268) 에 prop으로 전달된다 (라운드 1)
- `front/app` 전체 grep `(퇴장)` — 히트는 `scoreboard.ts:10`(주석)·`scoreboard.ts:15`·`RoundRevealOverlay.tsx:26` 3건 (라운드 1)
- `front/app` 전체 grep `joinScores|rankScores` — 정의 2건(`scoreboard.ts`), 사용 `RoundResultOverlay.tsx:19`·`RoundPanel.tsx:32` (라운드 1)
- `front/app/hooks/roundReducer.ts:65-88` — `ROUND_STARTED` 는 상태를 전체 교체하며 `winnerId: null` (85), `scores: state.scores` (86) 로 둔다 (라운드 1)
- `front/app/hooks/roundReducer.ts:90-108` — `ROUND_REVEALED` 는 `...state` 위에 `winnerId: e.winnerId`·`scores: e.scores` 를 덮어쓴다. `GAME_ENDED` 는 `{ ...state, phase: "ENDED", deadlineAt: null }` (108) (라운드 1)
- `front/app/hooks/roundReducer.ts:110-128` — `HYDRATE` 는 상태를 전체 교체하며 `winnerId: s.winnerId`·`scores: s.scores` 를 스냅샷에서 채운다 (라운드 1)
- `front/app/hooks/useRoomSubscription.ts:118-128` — `event.type === "LEFT"` 분기가 시작되는 위치. 본인 퇴장이면 `deactivate()`/`disconnectAndCleanup()` 후 return (라운드 1)

## OpenSpec 기존 스펙

- `openspec/specs/room-round-ui/spec.md` grep `^### Requirement:` — 9개 요구사항. 35행 `ROUND_REVEALED 수신 시 정답·승자·점수판을 표시한다`, 73행 `게임 종료 시 마지막 점수로 최종 결과 화면을 보여준다` (라운드 1)
- `openspec/specs/room-round-ui/spec.md:37` — "…점수판은 id만 담고 있으므로 방의 `players`와 조인해 닉네임으로 표기하고, 미매칭 id는 퇴장으로 폴백한다." 파일 전체 grep 결과 `players`·`조인`·`퇴장` 이 등장하는 유일한 줄 (라운드 1)
- `openspec/specs/room-round-ui/spec.md:35-72` — 해당 요구사항의 시나리오는 `승자가 있으면…`·`타임아웃이면…`·`정답 공개 구간에 정답 곡이 들린다`·`정답 공개 구간에만 영상이 보인다`·`OPEN 중에는 영상이 보이지 않는다` 5개 (라운드 1)
- `openspec/specs/room-round-ui/spec.md:73-84` — `게임 종료 시…` 요구사항의 시나리오는 `게임이 끝나면 최종 순위가 표시된다`·`서버 주도 종료도 행위자 없이 결과를 보여준다` 2개 (라운드 1)
- `openspec/specs/room-game-session/spec.md` grep `^### Requirement:` — 16개 요구사항. 65행 `게임 시작·종료 전이는 모든 참가자에게 실시간 전파된다`, 198행 `게임 중 퇴장한 플레이어는 점수판에서 제거된다`, 211행 `재접속 시 서버가 정답 없는 라운드 스냅샷을 제공한다` (라운드 1)
- `openspec/specs/room-game-session/spec.md:198-200` — "…그 플레이어를 점수판(Redis `room:{id}:scores`)에서 제거해야(SHALL) 한다…" (라운드 1)
- `openspec/specs/room-game-session/spec.md:211-226` — `재접속 시…` 요구사항의 시나리오는 `OPEN 중 재접속은 정답 없이 라운드를 복원한다`·`REVEAL 중 재접속도 다음 트랙을 선버퍼링할 수 있다` 2개 (라운드 1)

## 인프라

- `infra/app/compose.yml:5, 10-11` — 백엔드 서비스 `replicas: 2`, `update_config.parallelism: 1` (라운드 1)
