## Why

dev 환경에서 **어제 나간 방이 오늘까지 1/1로 남아 있다.** 원인을 추적한 결과, 방을 비우는 유일한 경로가 백엔드 프로세스 재시작 한 번에 끊기는 구조였다.

방 퇴장은 오직 한 경로로만 일어난다.

```
  WebSocket 끊김
      │  SessionDisconnectEvent
      ▼
  RoomDisconnectListener ──▶ ActiveSessionManager.removeSession (Redis, 세션 일치 시 1)
      │
      ▼
  ReconnectGracePeriodManager.scheduleLeave(roomId, playerId)
      │        ┌────────────────────────────────────────────┐
      │        │  ScheduledThreadPool + ConcurrentHashMap   │ ◄── JVM 메모리에만 존재
      │        └────────────────────────────────────────────┘
      ▼  60초 뒤
  RoomService.leave ──▶ 마지막 멤버면 roomRepository.delete
```

이 타이머는 `ReconnectGracePeriodManager`(`infrastructure/web/ReconnectGracePeriodManager.kt:18`)의 인-프로세스 스케줄러에만 있다. 어제 저녁 dev 백엔드가 두 번 롤링 배포됐다(21:20 `463c249`, 22:50 `4ddeee5`). `order: start-first`로 옛 컨테이너가 내려가는 순간 그 컨테이너가 쥐고 있던 소켓이 끊기고 유예가 예약되지만, 60초 뒤 실행할 JVM이 이미 없다. 새 컨테이너는 예약을 모른다. 프론트는 `reconnectDelay: 0`이라 재접속하지 않고 `onWebSocketClose` 핸들러도 없어 화면은 그대로 방 안이다. 이후 탭을 닫아도 서버에는 연결이 없어 아무 일도 일어나지 않는다. 방을 지울 다른 경로는 없다 — `player:session:` 키는 24시간 뒤 만료되지만 만료가 아무것도 트리거하지 않고, 고아 방을 정리하는 스케줄러도 없다.

**배포만의 문제가 아니다.** 같은 구조가 다음 상황에서도 방을 남긴다.

| 상황 | 무너지는 지점 |
|---|---|
| 헬스체크 실패·OOM으로 컨테이너 재시작 | 배포와 동일 — 예약 소실 |
| Redis 재시작(dev Redis는 볼륨·영속화 없음) | `player:session:*` 소실 → `removeSession`이 0 → 예약 자체가 안 됨 |
| 예약된 `leave`가 예외(락 획득 실패 등) | `ScheduledFuture`가 예외를 삼키고, 로그가 `leave` **뒤**에 있어 흔적도 없음 |
| 클라이언트가 FIN 없이 사라짐(절전·네트워크 단절) | STOMP 하트비트가 꺼져 있어(`enableSimpleBroker` 기본 0,0) 앱은 감지 못 하고 TCP/nginx 타임아웃에만 의존 |

**그리고 dev는 `replicas: 2`다.** 끊김이 A 인스턴스에서, 재접속이 B 인스턴스에서 처리되면 두 가지가 더 깨진다.

```
   A (소켓 끊김)                     B (재접속 CONNECT)
   ─────────────                     ──────────────────
   pendingLeaves[room,player]=timer   cancelGracePeriod() → 로컬 맵에 없음 → false
                                      ├─ 세션 키 있음   → 취소 실패를 무시하고 통과
                                      │                    └─ 60초 뒤 A의 타이머 → leave
                                      │                       ⇒ 접속 중인 사용자가 강제 퇴장 (phantom leave)
                                      └─ 세션 키 없음   → Room.join() → "이미 방에 입장한 플레이어" ConflictException
                                                          ⇒ 재접속 자체가 거부
```

즉 "끊김 → 유예 → 퇴장"이라는 멤버십의 핵심 상태 전이가 **단일 프로세스 수명과 단일 인스턴스**를 암묵적으로 가정하고 있고, 운영 환경은 이미 그 가정을 벗어났다.

## What Changes

유예 예약을 **프로세스 밖으로 꺼내 인스턴스 무관·재시작 내구성**을 갖게 하고, 서버가 끊김을 스스로 감지하도록 하트비트를 켜며, 인스턴스 종료 시 끊김이 기록될 시간을 확보한다. **범위는 백엔드와 infra다.** 클라이언트 자동 재접속(배포·순단이 참가자에게 무감해지는 마지막 조각)은 후속 change `add-client-auto-reconnect`로 분리한다 — 백엔드가 먼저 배포돼야 하고, 프론트는 테스트 프레임워크가 없어 검증 방식이 다르며, 관련 설계 결정이 전부 프론트 안에서 닫히기 때문이다.

```
  AS-IS                                     TO-BE
  ─────                                     ─────
  끊김 ─▶ 로컬 타이머(JVM) ─▶ leave          끊김 ─▶ Redis 예약(ZSET, score=만료시각)
                                                        │
  재접속 ─▶ 로컬 맵 취소                     재접속 ─▶ ZREM (어느 인스턴스에서든)
                                                        │
  (없음)                                     sweeper(@Scheduled + @SchedulerLock, 단일 replica)
                                             ─▶ 만료분 claim(ZREM) ─▶ RoomService.leave ─▶ 실패 시 복원
```

1. **유예 예약의 Redis 이관** — `ReconnectGracePeriodManager`의 스케줄러·맵을 Redis ZSET(member = `roomId:playerId`, score = 만료 시각)으로 대체한다. 예약·취소는 단일 키 연산이라 인스턴스 어디서 실행돼도 같은 결과다. 시각 앵커는 라운드 엔진과 같은 원칙으로 Redis 시계(`TIME`)를 쓴다.
2. **만료 처리의 sweeper화** — `RoundDeadlineSweeper`·`EventPublicationRetryScheduler`와 같은 `@Scheduled + @SchedulerLock` 패턴으로 만료된 예약을 처리한다. 항목을 먼저 `ZREM`으로 claim한 뒤 `leave`를 실행하고, 실패하면 5초 뒤로 복원해 자동 재시도하며 로그로 남긴다. `leave`는 이미 멱등하다(`Room.leave`가 없는 멤버면 no-op).
3. **재접속 판정의 단일화** — `RoomJoinChannelInterceptor`의 세 갈래(같은 방 세션 / 다른 방 세션 / 세션 없음)가 모두 Redis 예약을 기준으로 취소·입장을 결정한다. phantom leave와 "이미 입장한 플레이어" 오거부가 함께 사라진다.
4. **STOMP 하트비트 활성화** — 서버 `setHeartbeatValue` + `TaskScheduler`를 설정해 FIN 없는 단절을 앱이 스스로 감지하고, 유휴 중에도 프록시 타임아웃에 잘리지 않게 한다. 클라이언트(`@stomp/stompjs`)는 기본 10초 하트비트를 서버가 켜는 즉시 협상하므로 프론트 변경이 없다.
5. **종료 순서 보장** — `server.shutdown: graceful`과 compose `stop_grace_period`를 명시해, 인스턴스가 내려갈 때 열린 소켓이 닫히고 그 끊김 이벤트가 Redis 예약을 기록할 시간을 확보한다.

### 설계로 넘긴 결정 (design.md에서 확정)

- 예약의 source of truth — Redis ZSET vs MySQL `room_entry` 컬럼 → **Redis** (Decision 1)
- ZSET 키 샤딩 여부 → **단일 키** (Decision 1)
- sweeper 폴링 주기·`lockAtMostFor`·재시도 정책 → **1초 / PT4S / 5초 간격 무상한** (Decision 2)
- 유예 로직의 위치 — `infrastructure/web` 유지 vs `room` 모듈 포트화 → **`room` 모듈로 이동** (Decision 7)

### 알면서 감수하는 것

- **이 change만으로는 배포가 참가자에게 무감해지지 않는다.** 방은 정확히 정리되지만(끊김 → 60초 뒤 퇴장), 배포 중 접속해 있던 사용자는 여전히 끊기고 재접속하지 않는다. 무감 배포는 후속 `add-client-auto-reconnect`가 완성한다. 본 change는 그 전제인 "재접속이 어느 인스턴스에 떨어져도 통과한다"를 만든다.
- **Redis 재시작에는 여전히 취약하다.** 예약이 Redis에 있으므로 Redis가 날아가면 예약도 날아간다. 다만 이는 `player:session:*`가 이미 같은 위치에 있어 **새로 생기는 취약점이 아니라 기존 취약점이 옮겨가는 것**이다. dev Redis의 영속화(볼륨·`appendonly`)는 `infra/data` 소관이라 별도 change로 다룬다. 근본 백스톱(활성 세션을 하트비트로 갱신되는 lease로 만들고, lease가 끊긴 멤버를 리컨실하는 것)은 범위를 넘으므로 후속으로 남긴다.
- **SIGKILL 창은 남는다.** `stop_grace_period`를 넘겨 강제 종료되면 끊김 이벤트가 Redis에 기록되기 전에 죽을 수 있다. 위의 lease 백스톱 없이는 완전히 막을 수 없다.
- **이미 남아 있는 dev 고아 방은 코드가 고쳐주지 않는다.** 배포 후 SQL로 1회 정리한다. 기동 시 리컨실 로직을 넣어 "현재 접속자 없는 방"을 지우는 방법은 롤링 배포 중 다른 인스턴스의 접속자를 오판할 수 있어 채택하지 않는다.
- **하트비트로 유휴 트래픽이 생긴다.** 10초 주기의 작은 프레임이며, 연결당 비용은 무시할 수준이다.

## Impact

- **영향 스펙**:
  - `room-game-session` (MODIFIED) — "게임 중 끊긴 기존 멤버의 재접속은 입장 차단의 영향을 받지 않는다"의 구현 참조(`ReconnectGracePeriodManager` 로컬 판별)를 인스턴스 무관 판별로 고쳐 쓴다
  - `room-reconnect-grace` (ADDED) — 끊김→유예→퇴장의 내구성(재시작·복수 인스턴스), 실패 재시도, 하트비트 감지, 종료 시 기록 보장을 하나의 역량으로 정의한다. 지금은 어느 스펙도 이 전이를 소유하지 않는다. 후속 change가 이 스펙에 클라이언트 재접속 요구사항을 덧붙인다
- **영향 서브프로젝트**: `back/`(핵심), `infra/app/`(`stop_grace_period`). `front/` 변경 없음
- **영향 도메인 모듈**: `room` — `in/`(끊김 리스너·sweeper), `application/`(유예 포트·`RoomService.leave` 호출 경로), `out/`(Redis 예약 어댑터). `infrastructure/web`(`RoomJoinChannelInterceptor`, `WebSocketConfiguration`)
- **DB 스키마·ES 매핑·Kafka 토픽 영향**: 없음
- **Redis 키 영향**: 유예 예약 ZSET 신설. `player:session:*` 구조 불변
- **테스트**: `RoomLeaveIntegrationTest`·`RoomSessionReplaceIntegrationTest`·`RoomGameSessionIntegrationTest`가 `app.room.reconnect-grace-period-seconds=2`로 유예를 검증하고 있다. 유예 만료가 로컬 타이머가 아니라 sweeper 틱으로 오므로 `await` 대기 시간을 sweeper 주기까지 감안해 조정한다. "인스턴스 A에서 끊기고 B에서 취소"는 같은 Redis를 보는 두 매니저 인스턴스로 재현할 수 있다
- **배포 중 동작**: 이 변경을 배포하는 순간의 롤링 교체는 아직 옛 코드가 돌고 있어 예약이 한 번 더 유실될 수 있다. 배포 직후 고아 방 정리와 함께 처리한다
