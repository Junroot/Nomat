# Design — make-reconnect-grace-durable

## Context

방 멤버십의 핵심 전이 "끊김 → 유예 → 퇴장"은 현재 두 저장소에 걸쳐 있다.

```
                    Redis (인스턴스 공유)                JVM 메모리 (인스턴스 로컬)
                    ─────────────────────                ─────────────────────────
  활성 세션         player:session:{playerId}            ─
                    = {sessionId, roomId}, TTL 24h
  유예 예약         ─                                    ReconnectGracePeriodManager
                                                         ScheduledThreadPool + ConcurrentHashMap
  멤버십            (MySQL room_entry)                   ─
```

세션 판별은 이미 Redis에 있어 인스턴스 무관하지만, **그 판별의 결과로 예약되는 퇴장은 로컬**이다. 이 비대칭이 proposal의 모든 증상(배포 시 고아 방, `replicas: 2`에서의 phantom leave·재접속 오거부, 예외 삼킴)의 공통 원인이다.

재사용하는 기존 인프라는 다음과 같다.

- **sweeper 패턴**: `RoundDeadlineSweeper`·`EventPublicationRetryScheduler` — `@Scheduled(fixedDelay)` + `@SchedulerLock`(ShedLock, `RedisLockProvider`)으로 단일 replica만 폴링한다.
- **단일 시계**: 라운드 엔진의 모든 시각 앵커·비교는 `redis.call('TIME')`(`RoundStateStoreImpl`의 `NOW_MS` 프리앰블). 앱 시계 스큐를 correctness가 아닌 latency 문제로 강등한다.
- **마감 인덱스 조회**: `FIND_DUE_SCRIPT` = `ZRANGEBYSCORE key -inf now` — 만료 항목 조회의 원형.
- **멱등한 퇴장**: `RoomService.leave`는 방이 없으면 조용히 반환하고(`findById ?: return`), `Room.leave`는 멤버가 아니면 no-op이다. 같은 (roomId, playerId)를 두 번 처리해도 안전하다.
- **재접속 판정**: `RoomJoinChannelInterceptor.preSend(CONNECT)`의 세 갈래 — (a) 같은 방의 세션 있음 → 유예 취소, (b) 다른 방의 세션 있음 → 옛 방 유예 취소 + leave + 새 방 join, (c) 세션 없음 → 유예 취소 성공이면 통과, 실패면 join.
- **테스트 유예**: `IntegrationTest`가 `app.room.reconnect-grace-period-seconds=2`로 고정해 `RoomLeaveIntegrationTest`·`RoomSessionReplaceIntegrationTest`·`RoomGameSessionIntegrationTest`가 실제 시간으로 유예를 검증한다.

## Goals / Non-Goals

**Goals**

- 유예 예약이 **프로세스 재시작을 넘어** 살아남고, **어느 인스턴스에서든** 취소·실행된다
- 예약된 퇴장의 실패가 **로그에 남고 자동 재시도**된다
- FIN 없는 단절(절전·네트워크 단절)을 앱이 하트비트로 **스스로 감지**한다
- 인스턴스 종료 시 열린 소켓의 끊김이 Redis 예약으로 **기록될 시간**이 확보된다
- 유예 로직의 소유를 `infrastructure/web`에서 `room` 모듈로 옮겨 **의존 방향 역전을 해소**한다
- 프론트 변경 0, DB 마이그레이션·신규 외부 의존성 0

**Non-Goals**

- **클라이언트 자동 재접속** — 후속 change `add-client-auto-reconnect`. 본 설계는 그 전제(재접속 CONNECT가 어느 인스턴스에 떨어져도 유예 취소가 성립)를 만들고, 프론트 쪽에서 발견된 제약은 아래 "후속 change 인계"에 기록한다.
- **활성 세션의 lease화** — `player:session:*`를 하트비트로 갱신되는 짧은 TTL로 바꾸고 lease가 끊긴 멤버를 리컨실하는 백스톱. SIGKILL·Redis 소실까지 막는 근본책이지만 세션 모델 전체를 바꾸는 별개의 change다.
- **dev Redis 영속화** — `infra/data` 소관. 본 변경은 예약을 Redis에 두므로 Redis 소실 취약성은 "새로 생기는" 것이 아니라 `player:session:*`와 같은 자리로 "옮겨가는" 것이다.
- **기존 고아 방의 코드 정리** — 배포 후 SQL 1회. 기동 시 "접속자 없는 방 삭제"는 롤링 교체 중 다른 인스턴스의 접속자를 오판한다.
- **유예 시간 값의 변경** — 60초는 그대로.

## 구조

```
                          room 모듈
  ┌──────────────────────────────────────────────────────────────────────┐
  │  in/                                                                 │
  │    RoomDisconnectListener  ── SessionDisconnectEvent ──┐             │
  │    PendingLeaveSweeper     ── @Scheduled+@SchedulerLock┤             │
  │                                                        ▼             │
  │  application/                                    RoomService         │
  │    RoomService.scheduleLeave / cancelPendingLeave / sweepDueLeaves   │
  │    domain/PendingLeaveStore  (포트)                     │             │
  │                                                        ▼             │
  │  out/                                                                │
  │    PendingLeaveStoreImpl   ── Redis ZSET  rooms:pending-leaves       │
  └──────────────────────────────────────────────────────────────────────┘
                                   ▲
  infrastructure/web               │ (RoomService만 호출 — 기존과 동일 방향)
    RoomJoinChannelInterceptor ────┘
    WebSocketConfiguration      (하트비트)
```

`ReconnectGracePeriodManager`는 삭제된다. 그 두 메서드(`scheduleLeave`, `cancelGracePeriod`)의 자리는 `RoomService`가 맡고, 저장은 포트 `PendingLeaveStore` 뒤의 Redis 어댑터가 맡는다. `RoomDisconnectListener`는 이미 `room/in`에 있으므로 위치는 그대로다.

## Decisions

### Decision 1 — 예약의 source of truth는 Redis ZSET이다 (MySQL 컬럼이 아니라)

**결정**: 단일 키 `rooms:pending-leaves` ZSET. member = `"{roomId}:{playerId}"`, score = 만료 시각(ms, Redis `TIME` 기준).

| 연산 | 명령 | 원자성 |
|---|---|---|
| 예약 | Lua: `TIME` → `ZADD key <now+grace> member` | 단일 키 |
| 취소·claim | `ZREM key member` → 1이면 "예약이 있었다" | 단일 키 |
| 만료 조회 | Lua: `TIME` → `ZRANGEBYSCORE key -inf now` (member만, score 불필요) | 단일 키 |
| 복원 | Lua: `TIME` → `ZADD key <now + 재시도 간격 5s> member` (claim 후 leave 실패 시) | 단일 키 |

**대안 — `room_entry.disconnected_at` 컬럼 + MySQL 폴링**: 멤버십과 같은 트랜잭션에 묶이고 Redis 재시작에 살아남는다. 그러나 (1) Flyway 마이그레이션이 생기고, (2) 유예 판별의 나머지 절반(`player:session:*`)이 여전히 Redis라 Redis 소실 시 어차피 "세션 없음 → join 경로"로 오판되어 내구성 이득이 반쪽이며, (3) `Room`은 `@ElementCollection`이라 entry 하나의 컬럼만 갱신하려 해도 컬렉션 전체 dirty-check를 타고, (4) sweeper가 1초 단위 폴링을 MySQL에 걸게 된다. 라운드 엔진이 이미 "휘발성 조율 상태는 Redis, 영속 멤버십은 MySQL"로 선을 그어 두었고 유예 예약은 전자에 속한다.

**대안 — 샤딩된 키 `rooms:pending-leaves:{shard}`**: 예약·취소·조회가 전부 단일 키 연산이라 `CROSSSLOT` 제약이 없다. 방당 최대 20명, 활성 방 수십 개 규모에서 ZSET 하나가 핫키가 될 일도 없다. 샤딩은 sweeper 팬아웃만 늘린다. 다만 키 이름은 `RoundRedisKeys`처럼 한 곳(`PendingLeaveRedisKeys` 또는 `RoundRedisKeys` 확장)이 소유해, 나중에 라운드 키와 원자적으로 묶을 필요가 생기면 그때 `{shard}` 태그를 붙일 수 있게 한다.

### Decision 2 — 만료 처리는 sweeper, 단일 replica, claim-then-act

**결정**: `PendingLeaveSweeper`(`room/in`)가 `@Scheduled(fixedDelay = 1s)` + `@SchedulerLock(name = "pending-leave-sweep", lockAtMostFor = "PT4S")`로 `RoomService.sweepDueLeaves()`를 호출한다. 서비스는 만료 항목마다 **먼저 `ZREM`으로 항목을 claim하고(1이면 소유권 획득) 그 다음 `leave(roomId, playerId)`를 실행**한다(claim-then-act, 근거는 Decision 3). `leave`가 예외를 던지면 `warn` 로그 후 항목을 **`now + 5초`로 `ZADD` 복원**해 5초 뒤 틱에 재시도된다.

```
   틱마다
   ┌─ ZRANGEBYSCORE -inf now ─▶ [(r1,p1), (r2,p2), ...]
   │
   ├─ ZREM (r1,p1) = 1 ─▶ leave(r1,p1) ─ 정상 반환 ─▶ 끝
   ├─ ZREM (r2,p2) = 1 ─▶ leave(r2,p2) ─ 예외    ─▶ warn 로그, ZADD (r2,p2) now+5s ─▶ 5초 뒤 재시도
   ├─ ZREM (r3,p3) = 0 ─▶ 건너뜀 (재접속이 먼저 취소했다)
   └─ ...
```

**폴링 1초인 이유**: 유예가 60초라 1초 폴링은 과해 보이지만, (1) 테스트가 유예 2초로 실시간 검증하므로 폴링이 길면 테스트 대기가 그만큼 늘고, (2) `RoundDeadlineSweeper`와 같은 주기·같은 `lockAtMostFor`면 운영 감각이 하나로 통일되며, (3) 빈 ZSET의 `ZRANGEBYSCORE`는 O(log N) 한 번이라 비용이 없다. 주기는 상수 하나이므로 나중에 늘려도 된다.

**`lockAtMostFor = PT4S`인 이유**: `RoundDeadlineSweeper`와 동일한 Decision — sweeper가 주 구동기이므로 락 홀더 사망 시 회복을 폴링의 몇 배로 유계화한다. `EventPublicationRetryScheduler`의 `PT1M`을 복제하지 않는다. 한 항목의 `leave`가 멤버십 락 대기(`RedisDistributedLockExecutor`, 최대 50×100ms = 5초)로 4초를 넘길 수 있어 ShedLock이 먼저 풀리고 다른 replica가 sweep을 시작할 수 있는데, **claim-then-act 덕에 두 sweep이 같은 항목을 처리하지 않으므로 안전**하다. 락은 중복 방지가 아니라 부하 분산 장치다.

**스케줄러 스레드 격리**: 유예 sweep이 락 대기로 수 초를 점유할 때 `RoundDeadlineSweeper` 틱이 같이 밀리면 라운드 전이가 늦어진다(`room-game-session`이 허용한 "폴링 주기" 지연을 넘김). 그래서 `@Scheduled` 작업들은 스레드 여러 개를 가진 전용 스케줄러에서 돌아야 한다. 그런데 이 앱의 `@Scheduled` 스케줄러 해석은 겉보기와 다르다.

- `@EnableWebSocketMessageBroker`가 `messageBrokerTaskScheduler`(`TaskScheduler`, 풀 = CPU 코어 수)를 등록한다. Boot의 `taskScheduler` 자동설정은 `@ConditionalOnMissingBean(TaskScheduler)`라 **꺼져 있고**, `spring.task.scheduling.*` 프로퍼티는 소비자가 없다.
- 현재 `@Scheduled`는 컨텍스트의 유일한 `TaskScheduler`인 `messageBrokerTaskScheduler`에서 돌고 있다(스레드 = 코어 수). 즉 지금은 우연히 격리돼 있다.
- `TaskScheduler` 빈이 하나 더 생기면(Decision 6의 하트비트 빈) 타입 해석이 모호해지고, `taskScheduler`라는 이름의 빈도 없으므로 `@Scheduled`는 **단일 스레드 로컬 executor로 폴백**한다. 이러면 현재보다 나빠진다.

따라서 **`@Scheduled` 전용 스케줄러를 이름 `taskScheduler`로 명시 선언**한다(`@Bean(name = "taskScheduler")`, Boot의 `ThreadPoolTaskSchedulerBuilder`로 만들어 `spring.task.scheduling.pool.size: 4`·스레드명 접두사 `scheduling-`이 프로퍼티로 바인딩되게 한다). `TaskScheduler` 빈이 여러 개일 때 Spring은 이름 `taskScheduler`를 우선 해석하므로 두 sweeper와 `EventPublicationRetryScheduler`가 이 풀에서 돈다. 하트비트 빈(Decision 6)은 다른 이름을 써 이 해석에 끼어들지 않는다. 격리가 실제로 성립하는지는 통합 테스트에서 sweeper 실행 스레드명이 `scheduling-`로 시작하는지로 고정한다.

**완료와 실패의 판별**: `RoomService.leave`는 방이 없으면 조용히 반환하고, `Room.leave`는 멤버가 아니면 no-op이다. 따라서 **`leave`가 예외 없이 반환하면 완료**이며(방 없음·이미 퇴장 포함 — 둘 다 멱등 완료), 예외(대표적으로 락 획득 실패 `ConflictException`, DB 장애)면 복원해 재시도한다. 반환 타입을 바꿔 no-op 여부를 구분할 필요가 없다.

**재시도 정책**: 복원 score를 원래 만료 시각이 아니라 `now + 5초`로 두는 이유는 둘이다. (1) 원래 score를 유지하면 실패 항목이 매 틱(1초) 재시도되어 락 경합 중인 방에 로그가 초당 한 줄씩 쌓인다. (2) 항목이 과거 score에 머물러 노화하므로 시간 기반 GC와 충돌한다. 5초 간격이면 sweeper·로그 부하가 유계이고, 복원에 원래 score가 필요 없어 `findDue`가 member만 읽으면 된다. **재시도 상한과 시간 기반 GC는 두지 않는다** — 정상 경로에서 항목은 claim 또는 취소로만 소멸하고, 지속 실패는 5초마다 남는 `warn` 로그로 드러나야 할 운영 이슈다. 조용히 버리는 GC가 있으면 이 change가 고치려는 증상(고아 방)이 다른 얼굴로 재현된다.

### Decision 3 — 재접속 판정은 "ZREM의 반환값"이 한다

**결정**: `RoomJoinChannelInterceptor`의 세 갈래를 다음처럼 고친다. 로컬 맵이 없으므로 취소는 어느 인스턴스에서든 같은 결과다.

| 갈래 | AS-IS | TO-BE |
|---|---|---|
| (a) 같은 방 세션 있음 | 로컬 `cancelGracePeriod` (다른 replica면 false, 무시) | `cancelPendingLeave` (ZREM). 반환값은 무시 — 세션이 같은 방이면 멤버십은 이미 있다 |
| (b) 다른 방 세션 있음 | 옛 방 로컬 취소 → leave → join | 옛 방 `cancelPendingLeave` → leave → join. 동작 동일, 취소만 Redis |
| (c) 세션 없음 | 로컬 취소 성공이면 통과, 실패면 join | `cancelPendingLeave`가 1이면 통과, 0이면 join |

(c)가 핵심이다. 지금은 A에서 끊기고 B에 재접속하면 B의 맵이 비어 `join`을 타고 "이미 방에 입장한 플레이어"로 거부된다. ZREM은 A가 넣은 항목을 B가 지울 수 있으므로 통과한다. 그리고 A에 남는 타이머가 없으니 phantom leave도 없다.

**경합 — 만료와 재접속이 같은 순간**: sweeper가 `ZRANGEBYSCORE`로 항목을 읽은 직후 클라이언트가 ZREM으로 취소하고 CONNECT를 통과했는데, sweeper가 이어서 `leave`를 실행하면 접속 중인 사용자가 퇴장된다. 창은 sweeper의 "조회 → leave" 사이 수십 ms다. 이를 막는 것이 Decision 2의 claim-then-act다 — sweeper도 `leave` 직전에 **`ZREM`을 먼저 시도해 1일 때만 leave**한다. 취소가 먼저 ZREM했다면 sweeper의 ZREM은 0이라 건너뛴다.

```
   sweeper:   ZREM(member) == 1 ? leave() : skip
              leave 예외 → ZADD(member, now+5s)   ← 5초 뒤 재시도
   재접속:    ZREM(member) == 1 ? 통과 : join
```

두 경로 모두 ZREM으로 소유권을 가져가므로 한 항목을 둘이 동시에 처리하지 않는다. 남는 경합은 "sweeper가 claim한 뒤 leave 중에 재접속 CONNECT가 (c)로 들어와 ZREM 0 → join"이다. `join`과 `leave`는 같은 `room:{id}:lock`을 다투므로 결말은 락 순서에 따라 둘 중 하나다 — sweeper의 `leave`가 먼저면 멤버십이 지워진 뒤 `join`이 **재입장 성공**(정원·비밀번호·`PLAYING` 검증을 거침), `join`이 먼저면 멤버십이 아직 남아 `ConflictException("이미 방에 입장한 플레이어입니다.")`로 **거부**되고 직후 sweeper가 퇴장을 마친다. 어느 쪽이든 **연결된 세션이 멤버십 없이 남는 일은 없다**(거부는 CONNECT 자체의 실패라 세션이 열리지 않는다). 이는 유예 만료 시각에 정확히 겹친 재접속이므로 **유예 만료 후 재접속은 신규 입장**이라는 스펙과 부합하며, 거부된 경우의 재시도는 후속 프론트 change의 몫이다.

### Decision 4 — 시계는 Redis `TIME`, 앱 시계는 쓰지 않는다

**결정**: 예약 시 만료 시각 계산과 만료 조회 모두 Lua 안에서 `redis.call('TIME')`으로 한다. `RoundStateStoreImpl`의 `NOW_MS` 프리앰블을 그대로 공유한다.

앱 시계를 쓰면 replica 간 스큐가 "A가 예약한 항목을 B의 sweeper가 몇 초 이르게/늦게 처리"로 나타난다. 60초 유예에 몇 초 오차는 실용적으로 무해하지만, 라운드 엔진이 세운 규칙을 깨면서까지 얻는 것이 없다.

### Decision 5 — 인스턴스 종료는 graceful, 끊김 이벤트가 Redis에 닿게 한다

**결정**: `server.shutdown: graceful` + `spring.lifecycle.timeout-per-shutdown-phase: 20s`, compose `stop_grace_period: 30s`.

종료 시 Spring은 `SmartLifecycle.stop()` 단계에서 `SubProtocolWebSocketHandler`를 멈추며 열린 WebSocket 세션을 `GOING_AWAY`로 닫고, 그때 `SessionDisconnectEvent`가 발행된다. 이 단계는 빈 파괴(`RedisConnectionFactory` 포함) **이전**이므로 리스너의 `ZADD`가 Redis에 닿는다. 다만 이 순서는 Spring의 라이프사이클 페이즈 규약에 의존하므로 **통합 테스트로 고정**한다 — `ConfigurableApplicationContext.close()` 후 ZSET에 항목이 있는지 검증.

Swarm의 기본 `stop_grace_period`는 10초다. graceful 종료가 진행 중인 요청·소켓 정리에 그보다 오래 걸리면 SIGKILL이 오므로, 종료 페이즈 타임아웃(20초)보다 여유 있게(30초) 둔다. 이 값은 `infra/app/compose.yml`이 소유한다.

**남는 창**: SIGKILL(OOM 킬, `stop_grace_period` 초과)은 막을 수 없다. Non-Goal의 lease 백스톱이 이 창을 닫는다.

### Decision 6 — STOMP 하트비트 10초/10초, 서버가 `TaskScheduler`를 제공한다

**결정**: `enableSimpleBroker("/topic").setHeartbeatValue(longArrayOf(10_000, 10_000)).setTaskScheduler(scheduler)`. 스케줄러는 하트비트 전용 `ThreadPoolTaskScheduler` 빈(이름 `wsHeartbeatTaskScheduler`, 스레드 1개)을 새로 둔다. 이름을 `taskScheduler`로 두지 않는 이유는 Decision 2의 스케줄러 해석 규칙 때문이다 — 이 빈이 생기면 컨텍스트에 `TaskScheduler`가 세 개(`messageBrokerTaskScheduler`·`taskScheduler`·`wsHeartbeatTaskScheduler`)가 되며, `@Scheduled`는 이름 `taskScheduler`를 잡는다.

Spring의 `SimpleBrokerRegistration`은 `TaskScheduler`가 없으면 하트비트를 `0,0`으로 협상해 **양방향 모두 끈다**. 클라이언트(`@stomp/stompjs`)의 기본값 10초/10초는 서버가 켜는 순간 협상되어 프론트 변경이 필요 없다. `messageBrokerTaskScheduler` 빈을 주입해 재사용하는 방법은 설정 클래스와 그 부모 설정 사이의 순환 참조를 만들기 쉬워 전용 빈을 택한다.

**감지 경로**: 서버가 클라이언트 하트비트를 읽기 간격의 3배(Spring 기본 승수, ≈30초) 동안 못 받으면 `SimpleBrokerMessageHandler`가 세션을 끊고, 그 결과 WebSocket이 닫히며 **기존 `SessionDisconnectEvent` 경로로 합류**한다. 새 리스너가 필요 없다. 이 합류도 통합 테스트로 고정한다(하트비트를 보내지 않는 클라이언트로 접속 → 30초 남짓 뒤 예약이 생기는지).

**부수 효과**: 유휴 연결에도 10초마다 프레임이 흐르므로 nginx `proxy_read_timeout`(기본 60초)에 유휴 소켓이 잘리는 일이 없어진다. 지금까지는 라운드 이벤트가 없는 로비에서 이 타임아웃에 의존적이었다.

### Decision 7 — 유예 로직의 소유를 `room` 모듈로 옮긴다

**결정**: `infrastructure/web/ReconnectGracePeriodManager`를 삭제하고 포트 `room/application/domain/PendingLeaveStore` + 어댑터 `room/out/PendingLeaveStoreImpl`(`private class`)로 대체한다. `RoomService`가 `scheduleLeave(roomId, playerId)`·`cancelPendingLeave(roomId, playerId): Boolean`·`sweepDueLeaves()`를 노출한다.

현재 `infrastructure/web`(횡단 관심사)이 `room.application.RoomService`(도메인)를 의존하는 역전이 있다. 인터셉터는 프레임워크 경계라 도메인 서비스를 호출하는 것이 어쩔 수 없지만, "유예 예약"이라는 도메인 정책까지 거기 있을 이유는 없다. `ActiveSessionManager`(`infrastructure/redis`)는 "플레이어 ↔ 소켓 세션"이라는 웹 계층 관심사이므로 그대로 둔다.

포트 인터페이스:

```kotlin
interface PendingLeaveStore {
    fun schedule(roomId: Long, playerId: Long, graceSeconds: Long)
    /** 예약이 있었으면 true. 취소·claim 모두 이 연산이다. */
    fun remove(roomId: Long, playerId: Long): Boolean
    fun findDue(): List<PendingLeave>
    /** claim 후 실패한 항목을 now + 재시도 간격으로 되돌린다. */
    fun restore(roomId: Long, playerId: Long)
}
data class PendingLeave(val roomId: Long, val playerId: Long)
```

## 시퀀스 — 롤링 배포

본 change 배포 후, 후속 프론트 재접속이 아직 없을 때:

```
  클라이언트          nginx        인스턴스 A(구)        인스턴스 B(신)        Redis
     │                 │               │                    │                 │
     │═══ WS ═════════════════════════▶│                    │                 │
     │                 │          SIGTERM(graceful)         │                 │
     │◀── close ───────┼───────────────│ SessionDisconnect  │                 │
     │                 │               │── ZADD (r,p) now+60 ────────────────▶│
     │  (재접속 없음)    │               ✕ (JVM 종료)          │                 │
     │                 │                                    │  sweeper 60초 뒤 │
     │                 │                                    │── ZREM (r,p)=1 ▶│
     │                 │                                    │  leave → 방 삭제  │
```

방은 정확히 정리된다. 후속 change로 클라이언트가 유예 안에 재CONNECT하면 B의 `ZREM`이 1을 돌려 통과하고 sweeper는 항목을 못 찾는다 — 그것이 본 설계가 준비하는 경로다.

## Redis 키

| 키 | 타입 | member / field | score / value | TTL |
|---|---|---|---|---|
| `rooms:pending-leaves` | ZSET | `"{roomId}:{playerId}"` | 만료 시각 ms (Redis `TIME`) | 없음 — 항목은 claim/취소로만 소멸(Decision 2 재시도 정책). 시간 기반 GC 없음 |

`player:session:*`·`room:{id}:events` 채널·라운드 키·`SessionReplacedEventMessage` 스키마는 불변이다. Redis Pub/Sub 채널·Kafka 토픽 추가·변경은 없다.

## 테스트 전략

기존 통합 테스트 패턴(No Mocking, Testcontainers, `@IntegrationTest`, `await`)을 따른다. 유예는 계속 2초로 고정한다.

| 시나리오 | 방법 |
|---|---|
| 끊김 → 유예 만료 → 퇴장 | 기존 `RoomLeaveIntegrationTest` 유지. 대기 상한을 유예 + sweeper 주기 + 여유로 조정(`await().atMost(5s)`) |
| 끊김 → 유예 내 재접속 → 유지 | 기존 테스트 유지 |
| **다른 인스턴스에서 취소** | `RoomService`를 직접 호출해 `scheduleLeave` 후 `cancelPendingLeave`가 true — 두 인스턴스는 같은 Redis를 보므로 서비스 호출 두 번으로 재현된다. STOMP 두 인스턴스를 띄우지 않는다 |
| **leave 실패 재시도** | 락 키 `room:{id}:lock`을 테스트가 선점해 `ConflictException`을 유도 → 항목이 복원되어 남아 있는지 → 락 해제 후 다음 틱에 퇴장되는지. 존재하지 않는 방을 넣으면 예외 없이 항목이 사라지는지(멱등 완료) |
| **종료 시 예약 기록** | STOMP 접속 후 `subProtocolWebSocketHandler` 빈(`SmartLifecycle`)을 직접 `stop()` → 세션이 `GOING_AWAY`로 닫히고 ZSET에 항목이 생기는지, 이후 `start()`로 복구. `context.close()`는 쓰지 않는다 — Testcontainers가 컨텍스트 빈이라 같은 JVM의 다른 테스트를 깨뜨리고, `@IntegrationTest`가 `classes`를 명시해 중첩 `@TestConfiguration` 프로브도 등록되지 않는다. "라이프사이클 stop이 빈 파괴보다 앞선다"는 Spring 규약은 테스트하지 않고 신뢰한다 |
| **하트비트 미수신 끊김** | `WebSocketStompClient`는 `heart-beat` 헤더가 있으면 `TaskScheduler`를 강제하므로(`processConnectHeaders`의 `Assert.state`) 쓸 수 없다. 대신 `StandardWebSocketClient`로 raw 세션을 열어 `heart-beat:10000,10000`을 담은 CONNECT 프레임 텍스트를 직접 보낸 뒤 침묵 → 서버가 ≈30초 후 세션을 닫고 예약이 생기는지. 느리므로 `@Tag("slow")` 후보. 기존 `connectStomp` 헬퍼는 `defaultHeartbeat = {0,0}`이라 서버 하트비트를 켜도 영향 없음 |

## Risks / Trade-offs

- **[sweeper 락 홀더 사망 → 최대 4초 지연]** → `RoundDeadlineSweeper`와 동일한 유계. 유예 60초에 4초는 무감.
- **[락 경합 항목이 많으면 sweep 1회가 길어진다]** → 항목당 최대 5초 대기. 스케줄러 풀 분리로 라운드 sweeper에는 전파되지 않고, 복원 간격 5초가 같은 항목의 연속 재시도를 막는다. 방당 락 경합은 join/leave 임계구역뿐이라 실제로는 드물다.
- **[Redis 소실 → 예약·세션 동반 소실]** → 기존과 동일한 취약면. `infra/data` 영속화와 lease 백스톱은 별도 change. 본 변경으로 나빠지지 않는다.
- **[claim 후 leave 도중 SIGKILL → 항목이 사라진 채 멤버십 잔존]** → claim-then-act의 대가. 창은 `leave` 한 번의 실행 시간(수십 ms). lease 백스톱 전까지 감수한다. 대안인 "act-then-remove"는 Decision 3의 재접속 경합을 열어 접속 중인 사용자를 내보내므로 더 나쁘다.
- **[하트비트 도입으로 기존 유휴 소켓의 동작이 바뀜]** → 지금은 nginx 60초 타임아웃에 조용히 잘리던 로비 소켓이 살아 있게 된다. 이는 개선이지만, "로비에서 오래 방치된 탭"이 방을 계속 점유한다는 뜻이기도 하다. 탭이 닫히면 정상 끊김이므로 방치의 정의는 "열어둔 탭"뿐이고, 이는 의도된 동작이다.
- **[배포 순서]** → 본 변경 자체를 배포하는 롤링 교체는 옛 코드가 돌아 예약이 한 번 더 유실될 수 있다. 배포 직후 고아 방 SQL 정리를 같이 한다. 후속 프론트 재접속은 **반드시 본 변경이 나간 뒤**에 배포한다 — 먼저 나가면 옛 백엔드의 로컬 취소가 다른 replica에서 실패해 "이미 입장한 플레이어" 거부가 늘어난다.

## Migration Plan

1. 백엔드 배포 (Redis 키 신설은 마이그레이션 불필요, DB 변경 없음)
2. 배포 완료 후 dev 고아 방 정리 — 배포 시각 이전에 만들어진 `ACTIVE` 방 중 실제 접속자가 없는 것을 확인해 `room_entry` → `room` 순으로 삭제. 소수이므로 수동 확인 후 실행한다
3. `infra/app/compose.yml`의 `stop_grace_period` 반영 (infra-push-develop 워크플로)
4. 롤백: 백엔드를 이전 이미지로 되돌리면 로컬 타이머 방식으로 복귀한다. ZSET에 남은 항목은 아무도 읽지 않으므로 무해하지만 수동 `DEL`로 지운다

## 후속 change 인계 — `add-client-auto-reconnect`

본 설계 과정에서 프론트 재접속에 대해 확인된 사실과 결정이다. 후속 change의 design이 이를 출발점으로 삼는다.

- **`reconnectDelay`만 켜면 부족하다.** `connectToRoom`(`stomp.ts`)이 `Client`에 붙이는 `onStompError`·`onWebSocketError`가 둘 다 `client.deactivate()`를 호출하고 최초 연결 후에도 남아 있어, 재접속이 한 번 거부되거나 소켓이 한 번 안 열리면 재접속 루프가 끊긴다. 최초 연결용 핸들러는 Promise settle 후 해제하고, 재접속 구간은 재시도 카운트를 세는 핸들러가 맡아 소진 시에만 `deactivate()`한다.
- **구독은 `onConnect`에서 다시 걸어야 한다.** 현재 `useRoomSubscription`은 effect에서 한 번만 구독한다. 재접속 성공 시 재구독하고, 끊긴 사이 이벤트를 놓쳤을 수 있으므로 `fetchRoomDetail`로 `players`·`status`·`round`를 재조회한다(기존 `HYDRATE`의 `roundSeq` 단조 가드가 역행을 막는다).
- **끊김 안내를 사유별로 나눈다.** 기존 `isDeactivated` 문구는 "다른 탭에서 사용 중입니다" 하나뿐이라 재접속 실패에 합치면 틀린 원인을 보인다. 세션 대체 / 강제 퇴장 / 재접속 실패로 분기하고 재접속 실패 문구를 새로 둔다.
- **재접속 후 `SESSION_REPLACED` 자기 수신은 드문 경합이다.** 정상 graceful 종료에서는 끊김 이벤트가 먼저 처리되어 인터셉터 (c)를 타므로 발생하지 않고, SIGKILL 등으로 (a)를 타더라도 발행이 CONNECT `preSend` 안(CONNECTED보다 앞)이라 구독 전에 대개 지나간다. 남는 경합의 방어로 **재접속 성공 후 1초 안에 받은 `SESSION_REPLACED`를 무시**한다. 이벤트에 세션 id를 싣는 서버 변경은 Spring이 CONNECTED에 `session` 헤더를 싣지 않아 손이 커 택하지 않는다.
- **재접속 CONNECT는 같은 `connectHeaders`(roomId, password)로 보내면** 인터셉터 (a) 또는 (c)를 타고, 본 change 이후에는 어느 인스턴스에서든 통과한다.

## Open Questions

없음. sweeper를 `RoundDeadlineSweeper`와 통합해 ShedLock 트래픽을 줄이는 안은 검토했으나, 통합하면 유예 sweep의 락 대기가 라운드 전이를 직접 지연시키므로 **분리 + 스케줄러 풀 격리**(Decision 2)로 확정했다. 락 트래픽은 초당 키 2개로 무시할 수준이다.
