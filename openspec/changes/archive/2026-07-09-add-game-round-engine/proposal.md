## Why

선행 변경 `add-room-game-start`(아카이브됨)는 방장이 시작을 누르면 모든 화면이 `PLAYING`으로 바뀌는 끝-to-끝 슬라이스까지만 만들고, **라운드·정답·점수는 후속 "라운드 엔진"으로, 종료는 "서버 주도"로 확장**한다고 스펙에 못 박아뒀다. 그래서 지금 `PLAYING`은 빈 우산이다 — 프론트는 "곧 라운드가 시작됩니다" 플레이스홀더 한 줄(`front/app/routes/RoomView.tsx`)이고, 방 안에서 트랙을 재생하는 코드조차 없으며, 채팅은 서버를 거치지 않고 Redis로 곧장 발행된다(`RoomStompController.kt:46-59`).

본 변경은 그 우산 안을 채운다. 게임은 플레이리스트의 모든 트랙을 한 라운드씩 돌고, 라운드마다 클립을 재생해 채팅으로 정답을 받으며, 첫 정답자만 1점을 얻고, 정답 공개 후 다음 라운드로 자동 진행해 마지막 트랙 뒤 최고 득점자를 우승으로 종료한다.

핵심은 **서버가 라운드 시계의 주인**이라는 점이다. 경쟁형 맞히기에서 "누가 먼저 맞혔나"가 공정하려면 라운드 시작·마감·공개 시각을 서버가 소유해야 하고, 백엔드가 Swarm replica 2개로 뜨므로(`infra/app/compose.yml`) 그 타이밍이 다중 인스턴스·인스턴스 장애에서도 정확히 한 번만 전이돼야 한다. 이 변경은 그 라운드 전이 엔진을 기존 인프라 패턴만으로 짓는다.

비자명한 결정 네 가지가 이 설계를 끌고 간다 (각각 `design.md`에서 상술, 모두 다중 에이전트 적대 검증으로 도출됨):

- **이중 전이 방지의 단위는 분산 락이 아니라 단일 원자 Lua CAS다.** 기존 `RedisDistributedLockExecutor`는 TTL 5초 고정·펜싱 없음·비원자 GET-then-DEL 해제(`.kt:34-37`)라 상호배제를 보장하지 못한다. 전이 게이트를 `room:{id}:round` 키의 `(roundSeq, phase)` Lua CAS에 두면, Redis 단일 스레드가 동시 EVAL을 직렬화해 epoch당 정확히 한 번만 통과한다. 락에 멱등성을 걸면 GC·느린 Redis·배치 sweep로 임계구역이 5초를 넘는 순간 이중 채점·라운드 스킵이 난다(Decision 3).
- **타임아웃·정답 공개 전이엔 ms 정밀이 필요 없다.** 정밀이 중요한 유일한 경로(누가 먼저 맞혔나)는 타이머가 아니라 들어온 채팅 메시지로 즉시 처리된다(이벤트 구동). 남는 전이(아무도 못 맞힌 타임아웃, REVEAL 5초 후 다음 라운드)는 1~2초 늦어도 무감하다. 그래서 replica마다 모든 방의 로컬 타이머를 중복으로 들지 않고, **ShedLock으로 단일 replica만 도는 `@Scheduled` sweeper(약 1초 폴링)**가 마감 지난 라운드를 전이한다 — 타이머 부하를 replica 수만큼 복제하지 않는 단순·저부하 구조. Redisson·Quartz·keyspace 알림은 검증에서 모두 탈락했다(Decision 2).
- **정답은 OPEN 동안 클라이언트에 절대 내려가지 않는다.** 게임용 트랙 목록은 정답(`title`·`additionalTitles`)을 제거해(answer-stripped) 내려주고, 서버만 정답을 보유해 채팅을 대조한다. 재접속 복원 응답도 phase로 게이팅해 OPEN 중엔 정답을 빼고 REVEAL/ENDED에서만 포함한다 — 안 그러면 재접속·폴링만으로 치팅이 가능하다(Decision 6).
- **라운드 생명주기는 방 생명주기에 결합돼야 한다.** 게임 중 마지막 퇴장으로 방이 삭제됐는데 라운드 상태가 안 지워지면 sweeper가 빈 "유령 방"을 플레이리스트 끝까지 구동하고, 라운드 엔진의 종료와 DB `room.status`가 어긋나면 방이 입장 불가·재시작 불가로 벽돌화된다(Decision 7).

## What Changes

### back/ (`room` 모듈 — 라운드 엔진 신규)

**게임 상태 머신 (`application/domain`)**

- `RoundPhase` enum 신규: `OPEN`(클립 재생·정답 입력 창) / `REVEAL`(정답 공개) / `ENDED`(게임 종료). `RoomStatus`는 `PLAYING` 그대로 두고 건드리지 않는다 — `RoomStatus`는 멤버십·목록 노출 경계를, `RoundPhase`는 라운드 진행을 맡는 별개 관심사(Decision 1)
- 라운드 상태 포트 `RoundStateStore` 인터페이스 신규(`application/domain/`): `start`/`tryAdvanceOnDeadline`/`tryAdvanceOnCorrect`/`snapshot`/`teardown`. 모든 전이가 통과하는 단일 진입점
- 신규 도메인 이벤트 `RoundStartedEvent`, `RoundRevealedEvent`(`application/domain/`, 직렬화 안정성 위해 이 패키지에 위치). 게임 자연 종료는 기존 `GameEndedEvent`(ENDED) 재사용

**라운드 상태 저장·전이 (`out` — Redis Lua CAS)**

- `RoundStateStoreImpl`(`out/`, **`private class`**): `StringRedisTemplate` + Lua 스크립트로 라운드 상태를 원자 조작 — `ActiveSessionManager.kt:45-58`의 Lua CAS 패턴 확장
  - 키: 룸별 Hash `room:{id}:round` = `{roundSeq, phase, deadlineAt, trackIndex, winnerId, trackOrder}` + 전역 ZSET `rounds:deadlines`(score=deadlineAt, member=roomId) + 점수판 ZSET `room:{id}:scores`(member=playerId, score=누적점수)
  - 모든 전이는 단일 Lua: `(roundSeq, phase)` 게이트 + `now`/`deadline` 앵커는 `redis.call('TIME')` 단일 시계 + phase HSET과 deadline ZADD/ZREM을 한 스크립트로 원자화(Decision 3·4·5)
  - 라운드 상태·점수판에 GC 백스톱 TTL 부여(세션 24h 패턴 모방), 게임 종료·방 삭제 시 명시적 teardown(ZREM + DEL)

**라운드 오케스트레이션·정답 판정 (`application`)**

- `RoundService`(`application`) 신규: `tryAdvance` 단일 진입점으로 모든 트리거(sweeper·첫 정답·방장 종료)를 수렴. 전이 성공(CAS==1) 시에만 점수 부여(멤버 조건부)·다음 라운드 트랙 선정·`Round*Event` 등록
- `RoomStompController.chat`(`in`, **`private class` 유지**): `PLAYING` 동안 채팅을 정답 판정에 개입시킨다 — 정규화 후 현재 라운드 정답(`RoomPlaylistTrack.title` + `additionalTitles`)과 대조. **정답이면 채팅 원문을 방송하지 않고** `tryAdvanceOnCorrect`로 라운드를 닫아 `ROUND_REVEALED` 방송, **오답이면 기존대로 일반 채팅** 방송(정답 누출 차단, Decision 6)

**서버 주도 타이머 (sweeper 단독, `in`)**

- `RoundDeadlineSweeper`(`in`, **`private class`**): `@Scheduled`(약 1초) + `@SchedulerLock`로 단일 replica만 `rounds:deadlines` ZSET에서 마감 지난 미전이 라운드를 찾아 `RoundService.tryAdvance`로 전이 — `EventPublicationRetryScheduler.kt:14-18` 패턴 복제. 이것이 타임아웃·REVEAL 전이의 **유일한 구동기**(별도 로컬 타이머 없음 → replica마다 모든 방 타이머를 중복으로 들지 않음). sweeper가 주 구동기이므로 **`lockAtMostFor`는 30초 잡 선례(PT1M)를 복제하지 말고 폴링 주기의 약 2~4배(예: PT4S)** 로 둔다 — 락 홀더 사망 시 그만큼 자동 진행이 멈추기 때문(Decision 8)
- 정밀이 필요한 첫 정답은 sweeper가 아니라 `RoomStompController.chat`이 받은 메시지로 즉시 `tryAdvance`(이벤트 구동, 폴링 무관)

**전파·생명주기 결합 (`in`/`application`/`dto`)**

- `RoomEventListener`(`in`, **`private class` 유지**): `RoundStartedEvent`/`RoundRevealedEvent`를 받아 `room:{id}:events`로 발행 — `handleRoomJoined` 동형
- `RoomEventMessage`(`dto`)의 `@JsonSubTypes`에 `ROUND_STARTED`, `ROUND_REVEALED` 추가. `RoundStartedEventMessage`(roundSeq·totalRounds·deadlineAt·**answer-stripped 트랙 ref**: embedId·startTimeSec·endTimeSec·repeatCount), `RoundRevealedEventMessage`(roundSeq·winnerId·**정답 title**·점수판)
- 라운드 엔진 `ENDED` 전이가 도메인 이벤트로 **DB `room.status`를 `PLAYING→ACTIVE`로 멱등·비-방장-게이트 플립**(Decision 7). 방장 수동 `end`(기존 `/app/rooms/end`)도 같은 `tryAdvance` 경로로 통과시켜 Redis·DB를 함께 이동. **최종 점수판은 `ENDED`에 싣지 않고 마지막 `ROUND_REVEALED`가 전달**하므로 기존 메시지에 점수판 필드를 더하지 않는다. 단 `GameEndedEvent`/`GameEndedEventMessage`의 행위자(방장 `playerId`/`nickname`)는 **서버 주도 자연 종료에서 빈 값 가능**하도록 옵셔널로 조정(수동 종료=방장, 자연 종료=행위자 없음)
- `leave` 경로(방 삭제 포함)에서 라운드 상태 teardown 트리거(도메인 이벤트) + 점수판 `ZREM`. sweeper는 ZSET member에 대응 DB 방이 없으면 즉시 정리
- 재접속 복원: `RoomDetailResponse`(또는 전용 스냅샷 조회)에 현재 `RoundPhase`·`roundSeq`·`totalRounds`·`deadlineAt`·점수판·트랙 ref 포함. **OPEN이면 정답 제외**, REVEAL/ENDED에서만 정답 포함

### front/ — 본 변경 범위 아님 (후속 변경으로 분리)

방 화면 오디오 재생·라운드 UI(라운드 번호·남은 시간·점수판·`REVEAL` 정답·최종 결과)·`useRoomSubscription`의 `ROUND_STARTED`/`ROUND_REVEALED` 분기·재접속 화면 복원은 **후속 변경**에서 구현한다. 본 변경은 그 UI가 소비할 **서버 측 라운드 엔진과 실시간 프로토콜**(이벤트·answer-stripped 스냅샷·채팅 정답 판정·점수)까지만 다룬다. 정답 입력은 기존 채팅(`/app/rooms/chat`)을 서버가 정답으로 판정하는 방식이라 신규 프론트 입력 UI가 필요 없다(기존 채팅 입력 재사용).

### infra/

- **변경 없음.** Redisson·Quartz 의존성 불필요, Redis `notify-keyspace-events`(keyspace 알림) 설정 불필요 — sweeper가 기존 `StringRedisTemplate`+Lua+`@Scheduled`+ShedLock+pub/sub만 재사용하기 때문. `infra/data/compose.yml`의 Redis(단일 인스턴스)·`infra/app/compose.yml`(replica 2) 무변경

## Capabilities

### Modified Capabilities

- `room-game-session`: 기존 게임 세션 상태 전이(`ACTIVE↔PLAYING`)·입장 게이팅 능력에 **라운드 엔진** requirement를 ADD한다 — 라운드 자동 진행, 첫 정답 채점, 정답 공개·다음 라운드 전이, 자연 종료, 정답 비노출, 게임 중 퇴장 시 점수판 제거, 재접속 복원, 분산 안전·정확-한-번 전이. 더불어 기존 requirement **"게임 시작·종료 전이는 모든 참가자에게 실시간 전파된다"를 MODIFY**한다 — `ENDED`가 방장 행위자에 고정됐던 것을 서버 주도 종료(행위자 없음 가능)·라운드 이벤트(`ROUND_STARTED`/`ROUND_REVEALED`)까지 포함하도록 확장(선행 슬라이스 스펙이 "서버 주도 종료로 확장될 수 있다"고 이미 예고). 방장 수동 `/app/rooms/end`는 "방장 강제 종료"로 의미가 보존된다.

### New Capabilities

- 없음 (새 도메인 모듈 없이 기존 `room` 모듈·`room-game-session` 능력 위에 라운드 진행을 얹는다)

## Impact

- **서브프로젝트**: `back/`(room 모듈)만. `front/` UI는 후속 변경으로 분리. `infra/` 영향 없음
- **도메인 모듈**: `room`만 변경. `playlist`/`player`/`favoriteplaylist`/`auth` 무변경(트랙 정답은 기존 `RoomPlaylistTrack` 재사용, 서버 전용)
- **헥사고날 계층**:
  - `application/domain`: `RoundPhase`(enum 신규), `RoundStateStore`(포트 신규), `RoundStartedEvent`·`RoundRevealedEvent`(도메인 이벤트 신규)
  - `application`(서비스): `RoundService`(tryAdvance 단일 진입점·정답 판정·채점) 신규, `RoomService`에 라운드 teardown·status 동기화 연동
  - `application/dto`: `RoomEventMessage` 서브타입 2종 추가, `RoundStartedEventMessage`·`RoundRevealedEventMessage` 신규, `RoomDetailResponse`에 라운드 스냅샷 필드 추가
  - `in`: `RoundDeadlineSweeper` 신규(**`private class`**), `RoomStompController.chat`에 정답 판정 개입, `RoomEventListener`에 라운드 이벤트 리스너 추가 — 모두 `private class` 유지
  - `out`: `RoundStateStoreImpl`(Redis Lua CAS 어댑터, **`private class`**) 신규
- **DB 스키마**: **변경 없음**. 라운드 상태·점수판은 전부 Redis(휘발성)에 두고 MySQL에 영속화하지 않는다. `room.status`(`CHAR(20)`)는 기존 컬럼 재사용(라운드 엔진 ENDED가 `ACTIVE`로 플립). Flyway 마이그레이션 불필요, 롤백 가능. (Redis 재시작 시 진행 중 게임 상태는 소실 — 세션·유예와 동급의 휘발성 트레이드오프로 수용)
- **ES 매핑**: 해당 없음. 정답 매칭은 ES Nori가 아니라 서버 측 단순 정규화 비교(**모든 공백 제거 + 대소문자 무시**)로 처리 — 인덱싱·CDC 무관
- **Kafka 토픽**: 해당 없음
- **Redis 키/채널**:
  - **신규 키**: `room:{id}:round`(Hash, 라운드 상태), `rounds:deadlines`(전역 ZSET, 마감 시각 — 코드베이스 최초 ZSET 사용), `room:{id}:scores`(ZSET, 점수판). 모두 GC 백스톱 TTL + 명시적 teardown
  - **신규 채널 없음**: 전파는 기존 `room:{id}:events` pub/sub → `/topic/rooms/{id}` STOMP 재사용. 동시성은 기존 `room:{id}:lock`(멤버십 임계구역) 재사용하되 **라운드 전이 게이트는 락이 아니라 Lua CAS**(Decision 3)
  - **Redis 설정 변경 없음**: `notify-keyspace-events` 미사용
- **이벤트 직렬화**: `RoundStartedEvent`/`RoundRevealedEvent`는 `@TransactionalEventListener(AFTER_COMMIT)` ephemeral broadcast 경로(채팅·입퇴장과 동일 등급) — Modulith `event_publication` outbox 미적재, deserialization 부팅 리스크 없음. `room/application/domain/` 패키지 배치로 안정 위치 규칙 준수
- **스케줄링**: `@Scheduled`(약 1초)+`@SchedulerLock` 라운드 sweeper 1종만 추가(기존 `EventPublicationRetryScheduler`와 동일 ShedLock `RedisLockProvider` 재사용). 라운드 전용 로컬 `ScheduledExecutorService`는 두지 않는다 — sweeper 단독 구동(타이머를 replica마다 복제하지 않음)
- **API 계약 변화**: `GET /rooms/{roomId}`(또는 신규 스냅샷 조회) 응답에 라운드 스냅샷 필드 **추가**(하위호환). 신규 STOMP 아웃바운드 이벤트 `ROUND_STARTED`·`ROUND_REVEALED`. `/app/rooms/chat`은 시그니처 불변이나 `PLAYING` 중 의미가 "정답 시도"로 확장
- **의존성**: 추가/제거 없음 (Redisson·Quartz·db-scheduler 모두 불채택)
- **동작 변화**:
  - 방장 시작 → 라운드 엔진이 첫 `ROUND_OPEN`을 열고 `ROUND_STARTED` 발행 → 첫 정답(채팅) 또는 클립 소진으로 `ROUND_REVEALED`(정답·점수 공개, 5초) → 다음 라운드/마지막이면 `ENDED`(최고 득점자) → 방 `ACTIVE` 복귀. **관측 가능한 변화는 STOMP 이벤트·스냅샷 API 수준**(통합 테스트로 검증)이며, 화면 표현은 후속 front 변경
  - 게임 중 퇴장자는 점수판에서 제거, 방장이 나가도 게임은 서버 주도라 계속, 재접속 시 서버가 정답 없는 라운드 스냅샷 제공
- **롤백**: 단일 PR `git revert`. DB·인프라 변경이 없어 코드 원복으로 완전 롤백. 진행 중이던 라운드 Redis 키는 TTL로 자연 소멸하거나 운영상 `DEL`로 정리 가능
- **범위 외(후속 과제)**:
  - (1) **프론트엔드 전체** — 방 화면 오디오 재생·라운드 UI·결과 화면·재접속 화면 복원. 본 변경이 노출하는 이벤트·answer-stripped 스냅샷 위에서 후속 변경이 구현한다
  - (2) **"버퍼 완료 후 동시 시작"**(클라이언트 간 재생 시각 동기화) — `ROUND_OPEN` 시작 지점에 버퍼 ack 핸드셰이크 한 겹을 덧대는 확장으로, 머신·이벤트 스키마를 바꾸지 않고 나중에 얹힌다
