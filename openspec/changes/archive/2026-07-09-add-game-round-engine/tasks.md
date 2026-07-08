# Tasks — add-game-round-engine

> 적대 검증 도출 보정(`design.md` 동시성·실패 모드 표)을 각 태스크에 인라인으로 못 박는다. 백엔드 테스트는 **Testcontainers 기존 패턴(No Mocking)** 을 따르고 새 mock 인프라를 도입하지 않는다.

## 1. back/ — 라운드 상태 모델·전이 게이트 (Redis Lua CAS)

- [x] 1.1 `room/application/domain/RoundPhase.kt` enum 신규: `OPEN`, `REVEAL`, `ENDED` (`RoomStatus`는 무변경 — Decision 1)
- [x] 1.2 `room/application/domain/RoundStateStore.kt` 포트 인터페이스 신규: `start(roomId, trackOrder)`, `tryAdvanceOnDeadline(roomId, expectedSeq)`, `tryAdvanceOnCorrect(roomId, expectedSeq, winnerId)`, `snapshot(roomId)`, `teardown(roomId)`. 모든 전이가 통과하는 단일 진입점 계약
- [x] 1.3 `room/out/RoundStateStoreImpl.kt`(**`private class`**) 신규: `StringRedisTemplate` + `DefaultRedisScript`로 구현 — `ActiveSessionManager.kt:45-58` Lua 패턴 확장. 키 `room:{id}:round`(Hash: roundSeq·phase·deadlineAt·trackIndex·winnerId·trackOrder), `rounds:deadlines`(전역 ZSET), `room:{id}:scores`(ZSET)
- [x] 1.4 전이 Lua 스크립트 작성: `(roundSeq, phase)` 게이트로 epoch당 1회만 전이(Decision 3), `now`/앵커는 `redis.call('TIME')`(Decision 4), phase HSET과 deadline ZADD/ZREM을 **동일 스크립트**로(Decision 5). 다음 (seq,phase)는 Lua 내부 계산 — client가 target seq 못 주게(Decision 9). 조기 발화는 `-1` 반환
- [x] 1.5 라운드 상태·점수판에 GC 백스톱 TTL 부여(`ActiveSessionManager` 24h 패턴 모방), `teardown`은 ZREM + DEL 원자 수행

## 2. back/ — 라운드 오케스트레이션·정답 판정 (서비스)

- [x] 2.1 `room/application/RoundService.kt` 신규 — `tryAdvance` 단일 진입점으로 모든 트리거(sweeper·첫 정답·방장 종료) 수렴. CAS==1일 때만 후속(점수·다음 트랙 선정·`Round*Event` 등록), 0/-1은 no-op
- [x] 2.2 게임 시작 시 라운드 순서 셔플 후 `trackOrder` 고정(Decision 10), 첫 `ROUND_OPEN` 개시(`deadlineAt = openAt + (endTimeSec-startTimeSec)*repeatCount + 버퍼`)
- [x] 2.3 정답 판정: 정규화(**모든 공백 제거 + 대소문자 무시**, 입력·정답 양쪽 동일 적용) 후 현재 라운드 `RoomPlaylistTrack.title` + `additionalTitles` 대조 — 예: 정답 `밤을 달리다` ↔ `밤을 달 리다`·`밤 을 달리다` 모두 정답. 정답이면 `tryAdvanceOnCorrect`(단 `now<=deadline`이라야 인정 — 마감 후 정답 거부), 오답이면 일반 채팅 경로(Decision 6)
- [x] 2.4 채점: CAS가 커밋한 `winnerId`의 projection으로 `room:{id}:scores`에 `ZINCRBY +1`, `(roomId, roundSeq)` 멱등 키 + **"아직 멤버일 때만"** 원자 조건부(Decision 9)
- [x] 2.5 `REVEAL→다음 OPEN`(다음 트랙) / 마지막 트랙이면 `ENDED` 전이 — 기존 `GameEndedEvent`(ENDED) 재사용, `room.status`를 `PLAYING→ACTIVE`로 멱등·비-방장-게이트 플립(Decision 7). **최종 점수판은 ENDED에 싣지 않고 마지막 `ROUND_REVEALED`가 전달**(후속 front 결과 화면이 그 점수판 사용)
- [x] 2.6 `GameEndedEvent`/`GameEndedEventMessage`의 행위자(`playerId`/`nickname`=방장)를 **서버 주도 종료에서 빈 값 가능**하도록 옵셔널 처리 — 수동 종료는 방장, 자연 종료는 행위자 없음. ephemeral 경로라 직렬화 안정성 부담 없음(Decision 7, 선행 슬라이스 `GameEndedEventMessage` 시그니처 조정)

## 3. back/ — 서버 주도 타이머 (sweeper 단독, 인바운드)

- [x] 3.1 `room/in/RoundDeadlineSweeper.kt`(**`private class`**) 신규: `@Scheduled`(약 1초, fixedDelay) + `@SchedulerLock(lockAtMostFor≈"PT4S")`로 단일 replica만 `rounds:deadlines` ZSET의 마감 지난 미전이 라운드를 찾아 `RoundService.tryAdvance`로 전이 — `EventPublicationRetryScheduler.kt:14-18` 패턴. **타임아웃·REVEAL 전이의 유일 구동기**(별도 로컬 타이머 없음 → replica마다 타이머 중복 없음). sweeper가 주 구동기라 **`PT1M` 복제 금지**, 폴링의 2~4배로(Decision 8). ZSET member에 대응 DB 방 없으면 즉시 정리(Decision 7), Hash 화해 백스톱(Decision 5). 첫 정답(정밀 필요)은 sweeper가 아니라 채팅 메시지로 즉시 처리(태스크 2.3·4.5, 이벤트 구동)

## 4. back/ — 전파·이벤트·재접속 스냅샷 (in/dto)

- [x] 4.1 `room/application/domain/RoundStartedEvent.kt`, `RoundRevealedEvent.kt` 신규(`application/domain`, 직렬화 안정 위치). 게임 종료는 기존 `GameEndedEvent` 재사용
- [x] 4.2 `room/application/dto/RoundStartedEventMessage.kt`(roundSeq·totalRounds·deadlineAt·**answer-stripped** embedId·startTimeSec·endTimeSec·repeatCount), `RoundRevealedEventMessage.kt`(roundSeq·winnerId·**정답 title**·점수판) 신규
- [x] 4.3 `room/application/dto/RoomEventMessage.kt`의 `@JsonSubTypes`에 `ROUND_STARTED`, `ROUND_REVEALED` 추가
- [x] 4.4 `room/in/RoomEventListener.kt`(**`private class` 유지**)에 `@TransactionalEventListener(AFTER_COMMIT)` `handleRoundStarted`/`handleRoundRevealed` 추가 — `handleRoomJoined` 동형으로 `room:{id}:events` 발행
- [x] 4.5 `room/in/RoomStompController.kt`(**`private class` 유지**)의 `chat` 매핑에 정답 판정 개입(`PLAYING` 중 `RoundService` 경유), 방장 `end`를 `tryAdvance` 경로로 통과시켜 Redis·DB 동기화
- [x] 4.6 재접속 복원: 기존 `RoomController.getDetail`/`RoomDetailResponse`(`in`)를 확장해 `RoundStateStore.snapshot` 결과(RoundPhase·roundSeq·totalRounds·deadlineAt·점수판·트랙 ref)를 포함 — 신규 엔드포인트/컨트롤러를 새로 만들지 않음(부득이 신설 시 **`private class` 유지**). **OPEN이면 정답 제외**, REVEAL/ENDED만 정답 포함(Decision 6)
- [x] 4.7 `leave`/방 삭제 경로에 라운드 `teardown` + 점수판 `ZREM` 연동(도메인 이벤트, Decision 7)

## 5. back/ — 테스트 및 정적 분석

- [x] 5.1 도메인/단위 테스트(`room/application/domain/`): `RoundPhase` 전이 규칙, 정답 정규화·매칭 로직(순수 Kotlin, Spring 컨텍스트 없이 — 기존 `RoomTest` 패턴·한국어 백틱 네이밍)
- [x] 5.2 통합 테스트(`@IntegrationTest` + Testcontainers Redis/MySQL): Lua CAS 멱등성 — 동시 `tryAdvanceOnDeadline`/`tryAdvanceOnCorrect` 호출 시 정확히 1회 전이·단일 winner. 과거 deadline을 ZSET에 직접 ZADD 후 sweeper 호출로 **마감 라운드 전이 구동** 실측(sweeper가 주 구동기). **컨테이너 stop 등 인프라 조작·새 mock 도입 금지**
- [x] 5.3 통합 테스트(STOMP): 방장 시작 → `ROUND_STARTED` 수신(정답 미포함 검증), 채팅 정답 → `ROUND_REVEALED`(정답 포함)·점수 +1, 오답 → 일반 `CHAT`, 마지막 라운드 → `ENDED`로 `room.status` `ACTIVE` 복귀. 기존 STOMP 테스트 패턴·Awaitility·`RoomStep` 픽스처 재사용
- [x] 5.4 통합 테스트: 게임 중 퇴장 시 점수판 `ZREM` + 라운드 teardown, 재접속 스냅샷에 OPEN 정답 비노출, 유령 방(삭제 후 ZSET 고아) sweeper 정리
- [x] 5.5 `./gradlew test` 전체 통과
- [x] 5.6 `./gradlew detekt` 통과

> **프론트엔드는 본 변경 범위 아님 — 후속 변경으로 분리.** 방 화면 오디오 재생·라운드 UI·결과 화면·재접속 화면 복원은 본 변경이 노출하는 이벤트(`ROUND_STARTED`/`ROUND_REVEALED`/`ENDED`)·answer-stripped 스냅샷·채팅 정답 판정 위에서 후속 변경이 구현한다. 따라서 `npm run typecheck`/`npm run build` 태스크도 본 변경엔 없으며, 검증은 STOMP 통합 테스트(태스크 5.3·5.4)로 한다.

## 6. infra/

- [x] 6.1 변경 없음 — 신규 의존성(Redisson·Quartz)·Redis `notify-keyspace-events` 설정 불필요 확인만(sweeper가 기존 인프라만 재사용)

## 7. 문서

- [x] 7.1 (선택) `back/CLAUDE.md`에 라운드 엔진(sweeper 단독 구동 타이머·`room:{id}:round`/`rounds:deadlines`/`room:{id}:scores` Redis 키·`ROUND_STARTED`/`ROUND_REVEALED` 이벤트·"전이 게이트는 락 아닌 Lua CAS" 규칙) 부기
