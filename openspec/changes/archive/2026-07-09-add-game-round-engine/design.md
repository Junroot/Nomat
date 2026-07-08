# Design — add-game-round-engine

## Context

`add-room-game-start`가 `RoomStatus.PLAYING`과 게임 시작/종료 전이·입장 게이팅을 깔아뒀지만 `PLAYING` 내부는 비어 있다. 본 변경은 그 안에 라운드 진행을 채운다. 설계는 탐색 대화에서 사용자가 확정한 규칙(아래)과, 분산 타이머 방식을 다룬 다중 에이전트 적대 검증(설계 패널 3 + 적대 렌즈 3, 모두 *survives-with-fixes* 수렴)의 결론을 반영한다.

확정된 게임 규칙:
- **라운드 = 플레이리스트의 모든 트랙**(트랙 수 = 총 라운드 수)
- **첫 정답자만 1점**, 라운드별 누적 점수의 **최고자가 우승**
- 라운드 종료 = **첫 정답** 또는 **클립이 `repeatCount`회 재생 완료(타임아웃)** 중 먼저
- 정답 공개(`REVEAL`)를 5초 가진 뒤 **곧장 다음 라운드**(별도 카운트다운 없음)
- 게임 중 퇴장한 플레이어는 **점수판에서 제거**, 방장이 나가도 게임은 멈추지 않음
- **재접속 복원** 지원
- **버퍼 완료 후 동시 시작은 후속 과제**(범위 외)

관련 기존 인프라(전부 재사용):
- 실시간 fan-out: 도메인 이벤트 → `room:{id}:events` Redis pub/sub → 각 replica `RoomEventRedisSubscriber` → `/topic/rooms/{id}` STOMP. 모든 replica가 `PatternTopic("room:*:events")` 구독(`RoomEventRedisSubscriber.kt:26`)
- Redis Lua CAS 선례: `ActiveSessionManager.kt:45-58`(`StringRedisTemplate` + `DefaultRedisScript` 원자 compare-and-delete)
- 단일-인스턴스 주기 작업 선례: `EventPublicationRetryScheduler.kt:14-18`(`@Scheduled` + `@SchedulerLock`, ShedLock `RedisLockProvider`)
- 멤버십 직렬화 락: `withLock("room:{id}:lock")`(`RoomService`) — 단, **라운드 전이 게이트로는 쓰지 않는다**(Decision 3)

## Goals / Non-Goals

**Goals**
- `PLAYING` 안의 라운드 상태 머신(`OPEN → REVEAL → (다음 OPEN | ENDED)`)과 서버 주도 자동 진행
- 첫 정답 채점·누적 점수판·우승 판정
- 채팅 기반 정답 판정과 **정답 비노출**(answer-stripped)
- 다중 인스턴스·인스턴스 장애에서 **정확히 한 번** 전이(분산 안전) + 정상 경로 **ms 정밀**
- 게임 중 퇴장 시 점수판 제거, 재접속 시 정답 없는 라운드 복원
- 신규 외부 의존성·인프라 변경 0

**Non-Goals**
- **버퍼 완료 후 동시 시작**(클라이언트 간 재생 시각 정밀 동기화) — 후속 과제. `ROUND_OPEN` 시작에 버퍼 ack 핸드셰이크를 덧대는 확장이라 본 머신·이벤트 스키마 불변
- **프론트엔드 전체** — 방 화면 오디오 재생·라운드 UI·결과 화면·재접속 화면 복원은 후속 변경. 본 변경은 백엔드 라운드 엔진과 실시간 프로토콜(이벤트·answer-stripped 스냅샷·채팅 정답 판정·점수)까지만 다루고, 검증은 STOMP 통합 테스트로 한다
- 게임 전적의 **MySQL 영속화** — 점수판·라운드 상태는 휘발성 Redis. Redis 재시작 시 진행 중 게임 소실은 세션·유예와 동급 트레이드오프로 수용
- 라운드별 차등 점수·시간 보너스 — 첫 정답 1점 고정
- Redis HA/클러스터 — 단일 Redis SPOF는 기존 락·세션·pub/sub과 동일 의존, 본 변경 범위 밖

## 상태 머신

`RoomStatus.PLAYING`은 우산이고, 그 안에서 `RoundPhase`가 라운드를 구동한다.

```
RoomStatus:  PENDING ─join─▶ ACTIVE ─방장 start─▶ PLAYING ─게임 종료─▶ ACTIVE
                                                    │
RoundPhase (PLAYING 내부, sweeper 폴링/첫 정답 구동):  │
                                                    ▼
  게임 시작 ──▶┌───────────────┐
       ┌──────▶│   ROUND_OPEN  │  클립 repeatCount회 재생, 채팅=정답 입력
       │       │ deadlineAt 설정│  deadlineAt = openAt + (end−start)×repeat + 버퍼
       │       └──┬─────────┬──┘
       │   첫 정답자 발생   클립 소진(타임아웃)
       │   winner=그 사람   winner=없음
       │      (이벤트, 즉시)  (sweeper 폴링)
       │          └────┬────┘
       │               ▼
       │       ┌───────────────┐
       │       │ ROUND_REVEAL  │  정답·정답자·점수판 공개 (5초)
       │       └──┬─────────┬──┘
       │  다음 라운드 有   마지막 라운드
       └─(sweeper)─┘       (sweeper)
                            ▼
                    ┌───────────────┐
                    │     ENDED     │  최종 점수판(우승) → room.status=ACTIVE
                    └───────────────┘
  ※ 어느 phase든: 방장 강제 종료 또는 방 비움 → ENDED/teardown
```

`ENDED`는 별도 "결과 화면 단계"를 서버에 두지 않는다. **최종 점수판은 마지막 라운드의 `ROUND_REVEALED`가 이미 싣고 있으므로**, `ENDED`(기존 `GameEndedEvent`)는 점수판을 다시 싣지 않고 "게임 종료" 신호만 전하며 방을 곧장 `ACTIVE`로 되돌린다 — 기존 메시지에 점수판 필드를 더할 필요가 없다. 단 기존 `ENDED`는 종료를 누른 방장(`playerId`/`nickname`)을 실었는데, 서버 주도 종료엔 방장 행위자가 없을 수 있으므로 **행위자 필드를 옵셔널(서버 종료 시 빈 값)로** 다룬다(`@TransactionalEventListener` ephemeral 경로라 직렬화 안정성 부담 없음). 결과 오버레이의 표시 시간은 프론트가 관장한다.

## Decision 1 — `RoomStatus.PLAYING`은 우산, 라운드는 중첩 `RoundPhase`

`RoomStatus`에 `OPEN`/`REVEAL`을 직접 넣지 않는다. `RoomStatus`는 "PLAYING이면 목록 제외·입장 차단"이라는 규칙의 집행점(`Room.join():84`, `RoomService.get():41`)이고, 라운드 단계는 수 초마다 바뀌므로 섞으면 그 규칙이 단계마다 흔들린다. 별개 `RoundPhase`(휘발성 Redis 상태)로 분리해 입장 가드는 `PLAYING` 하나로 유지한다.

## Decision 2 — 2계층 라운드 엔진: 진실(Lua CAS) + 구동(ShedLock sweeper), 첫 정답은 이벤트 구동

**타임아웃·정답 공개(REVEAL) 전이엔 ms 정밀이 필요 없다.** 정밀이 중요한 유일한 경로(누가 먼저 맞혔나)는 타이머가 아니라 들어온 채팅 메시지로 즉시 처리되기 때문이다. 그래서 replica마다 모든 방의 로컬 타이머를 중복으로 들지 않고, **단일 replica만 도는 ShedLock 폴링 sweeper**가 마감 지난 전이를 구동한다.

```
  진실(truth)   room:{id}:round Hash + rounds:deadlines ZSET, 단일 Lua CAS 전이
       ▲ 구동(driver)                          ▲ 즉시 경로(정밀 필요)
  ShedLock @Scheduled sweeper             첫 정답 = 이벤트 구동
  (단일 replica, 약 1초 폴링)              들어온 채팅 메시지를 받은
  rounds:deadlines에서 마감 지난           replica에서 즉시 tryAdvance
  라운드를 tryAdvance                      (폴링·타이머 무관)
  = 타임아웃·REVEAL 전이의 유일 구동기
```

타임아웃·REVEAL은 ~1초 폴링 지터를 허용한다(타임아웃 1~2초 늦음·REVEAL 5~6초 = 무감, 조사로 확인된 적정 범위 안). 첫 정답은 폴링과 무관하게 즉시 전이하므로 버즈 순서 공정성은 유지된다. **replica마다 모든 방 타이머를 복제하지 않으므로 부하가 replica 수에 비례해 늘지 않는다** — 타이머 일은 단일 sweeper가 전담하는 저부하 구조.

대안은 모두 탈락:

| 후보 | 탈락 이유 |
|---|---|
| 로컬 one-shot 타이머(replica별) | 이 전이들이 ms 정밀을 요구하지 않으므로, replica마다 전 방 타이머를 중복으로 들고 전이마다 replica 수만큼 CAS를 쏘는 비용을 정당화하지 못함(부하가 replica 수에 비례). 정밀이 필요한 첫 정답은 어차피 이벤트 구동이라 타이머가 불필요 |
| Redisson `RDelayedQueue` | 발화에 Redis 왕복이 끼고 팀이 일부러 피한 2번째 Netty Redis 클라이언트(`RedisDistributedLockExecutor`는 직접 구현)가 필요. 분산 회수 이점은 ShedLock sweeper가 이미 제공 |
| Quartz clustered | 초 단위 폴링 획득, 방·라운드마다 MySQL row churn, 11개 테이블 마이그레이션 — 수초짜리 ephemeral 타이머에 미스핏 |
| keyspace 만료 알림 | `infra/data` 수동 배포 노드에 `notify-keyspace-events` 설정 필요, active-expire(hz=10) tail 지터 상한 미보장, at-most-once(미수신), 전역 만료 firehose |
| 무(無)타이머(클라 클립종료 보고) | 대량 disconnect·백그라운드 탭 스로틀링·파티션으로 클라가 침묵하면 라운드가 안 끝나 "게임이 멈추지 않아야 한다" 위반 |

모든 전이 트리거(sweeper·첫 정답·방장 종료)는 단일 진입점 `RoundService.tryAdvance`로 수렴한다. 신규 컴포넌트는 둘뿐이고 둘 다 기존 패턴 복제다: `RoundStateStoreImpl`←`ActiveSessionManager`, `RoundDeadlineSweeper`←`EventPublicationRetryScheduler`.

## Decision 3 — 이중 전이 방지의 단위는 분산 락이 아니라 단일 원자 Lua CAS (★ 핵심)

가장 비자명하고 가장 치명적인 결정. 기존 `RedisDistributedLockExecutor`는 **TTL 5초 고정·펜싱 토큰 없음·해제가 비원자 GET-then-DEL**(`.kt:34-37,42`)이라 상호배제를 보장하지 못한다. GC pause·느린 Redis RTT·sweeper가 한 틱에 due 방 다수 순회로 임계구역이 5초를 넘으면 락이 자동 만료돼 두 인스턴스가 겹친다:

```
sweeper 인스턴스 S          정답 인스턴스 F
─────────────────────────────────────────────
락 획득, 전이 처리 중
 …5초 초과로 락 자동 만료…
                          같은 방 락 재획득(setIfAbsent)
phase=OPEN 읽음(S 미기록)   phase=OPEN 읽음
REVEAL 기록(winner=null)    REVEAL 기록(winner=X, +1)
─────────────────────────────────────────────
결과: conflicting winner 쓰기 + 이중 채점, 정답 2건 겹치면 epoch 2점프로 라운드 스킵
```

따라서 전이 게이트를 **락에서 떼고** `room:{id}:round` Hash의 단일 원자 Lua CAS로 옮긴다:

```
tryAdvance(roomId, expectedSeq, expectedPhase, [winnerId]):  -- 단일 EVAL
  local now = redis.call('TIME')                              -- Decision 4
  local seq, phase, deadline = HGET room:{id}:round ...
  if seq ~= expectedSeq or phase ~= expectedPhase then return 0 end   -- 진 경로 no-op
  if expectedPhase == 'OPEN' and winnerId == nil and now < deadline then return -1 end  -- 조기발화 자가보정
  -- 다음 (seq,phase)는 Lua가 내부 계산 (client가 target seq 못 줌, Decision 9)
  HSET room:{id}:round phase=<next> deadline=<next> winnerId=<...>
  ZADD/ZREM rounds:deadlines ...                              -- Decision 5: 같은 스크립트
  return 1
```

Redis 단일 스레드가 동시 EVAL을 직렬화하므로 `(seq, phase)`가 기대값과 같은 첫 EVAL만 1을 반환하고 나머지는 0이다. 멱등성이 락이 아니라 CAS에 있으므로 락의 TOCTOU에 의존하지 않는다 → **라운드 전이 핫패스에서 `withLock`을 제거**(RTT 절감 + 락 결함 비의존). 멤버십 임계구역(`join`/`leave`)은 기존대로 락을 유지한다.

> 적대 검증 결과: 정답-vs-타임아웃, 정답X-vs-정답Y, REVEAL-vs-next, ShedLock 만료 중 sweeper 중복, STOMP 정답 재전달 — 모든 인터리빙을 이 CAS가 0 반환으로 흡수한다(*exactly-once 전이, exactly-one winner*).

## Decision 4 — 단일 시계: `redis.call('TIME')`을 세 곳에 통일

시계 스큐를 방치하면 정확도 버그다. NTP가 깨져 라운드 시작 인스턴스의 시계가 빠르면 절대 `deadlineAt`이 미래로 박히고, 느리면 이미 과거가 되어 라운드가 즉시 종료된다. `redis.call('TIME')`을 **① deadline 앵커(라운드 시작 Lua 안에서 `start=TIME; deadline=start+duration`), ② 게이트 비교(`now>=deadline`), ③ sweeper 선택(`ZRANGEBYSCORE -inf TIME`)** 세 곳에 통일한다. 그러면 N개 앱 시계 도메인이 Redis 1개로 collapse돼 스큐가 correctness 버그에서 latency 문제로 강등된다. 로컬 시계는 "언제 시도할지"만 좌우하고 "허용 여부"는 Redis 시계가 재검사(조기 발화는 `-1` 반환 후 재예약).

## Decision 5 — phase HSET과 deadline ZADD/ZREM은 동일 Lua로 원자화

라운드 시작·정답·`REVEAL→next` 모든 전이에서 phase 기록과 deadline 등록을 **한 스크립트**에 둔다. 분리하면 HSET 커밋 후 ZADD 전에 인스턴스가 죽는 창이 생기고, 그러면 `room:{id}:round`엔 진행 상태가 있는데 `rounds:deadlines` ZSET엔 member가 없어 **sweeper가 영영 못 찾는 영구 고아 → 라운드 STUCK**(liveness 위반)이 된다. 백스톱으로 sweeper는 ZSET뿐 아니라 "Hash에 마감 지났는데 미전이"인 방도 화해(reconcile)한다.

## Decision 6 — 정답 판정은 채팅 서버 개입, OPEN 중 정답 절대 비노출

`PLAYING` 동안 `/app/rooms/chat`은 곧 정답 시도다. 서버가 메시지를 정규화(**모든 공백 제거** + 대소문자 무시)해 현재 라운드의 `RoomPlaylistTrack.title` + `additionalTitles`와 대조한다(입력·정답 양쪽에 같은 정규화 적용). 예: 정답이 `밤을 달리다`면 `밤을 달 리다`·`밤을     달리다`·`밤 을 달리다`가 모두 정답 처리된다. 단 **마감 시각 이후 도착한 정답은 거부**한다(`now<=deadline` Redis TIME 게이트 — sweeper가 닫기 전 OPEN 잔여 창의 늦은 추측도 무효).

- **정답**: 채팅 원문을 방송하지 **않고**(누출 차단) `tryAdvanceOnCorrect`로 라운드를 닫아 `ROUND_REVEALED` 방송
- **오답**: 기존대로 일반 `CHAT` 방송

정답이 클라이언트로 새는 두 경로를 모두 막는다:
- 게임 시작 시 클라에 내려가는 per-game 트랙 목록은 **정답 제거(answer-stripped)** — 서버만 정답 보유
- `ROUND_STARTED`/재접속 스냅샷은 OPEN 동안 `{phase, roundSeq, deadlineAt, embedId, startTimeSec, endTimeSec, repeatCount}`만 포함, `title`/`additionalTitles`는 **REVEAL/ENDED에서만** 포함

> 적대 검증이 지적한 치팅 경로: 재동기화 응답이 `trackIndex`만 줘도 클라가 트랙 메타로 정답을 역추적할 수 있다 → 스냅샷을 phase로 게이팅해 OPEN 중 정답 관련 필드를 전부 배제한다. (잔여 한계: `embedId`로 YouTube 영상 자체를 역추적하는 건 클라 재생 구조상 불가피 — 캐주얼 수준 수용, 오디오 프록시는 후속.)

## Decision 7 — 라운드 생명주기를 방 생명주기에 결합

멱등 CAS의 진실원천(Redis Hash)이 DB 방 생명주기와 분리되면 게이트가 막지 못하는 구멍이 둘 난다(적대 검증 HIGH 2건):

- **유령 방**: 게임 중 마지막 퇴장으로 `RoomService.leave`가 빈 방을 `delete`(`RoomService.kt:122-134`)하지만 라운드 상태를 안 지우면, sweeper가 삭제된 방을 플레이리스트 끝까지 전이·브로드캐스트하고 사이드이펙트가 없는 방을 참조해 outbox poison까지 유발. → **방 삭제/게임 중 빈 방 시 라운드 teardown**(도메인 이벤트로 `rounds:deadlines` ZREM + `room:{id}:round` DEL + 점수판 DEL), sweeper는 ZSET member에 대응 DB 방이 없으면 즉시 정리, Hash엔 TTL 백스톱
- **방 벽돌화**: 라운드 엔진 `ENDED`와 방장 전용 DB `room.status`가 안 맞으면 Redis는 종료·DB는 `PLAYING` 고착 → 신규 입장 거부(`Room.join:84`)·재시작 불가(`Room.start:103`). 역방향(방장 `end()`가 Redis 미정리)이면 zombie 라운드. → **라운드 `ENDED` 전이가 도메인 이벤트로 `room.status`를 `PLAYING→ACTIVE`로 멱등·비-방장-게이트 플립**, 방장 수동 `end`도 같은 `tryAdvance` CAS 경로로 통과(epoch++ → ENDED, ZREM)해 Redis·DB를 함께 이동. start/end/round-ENDED 모두 단일 진입점 수렴

## Decision 8 — sweeper가 주 구동기이므로 `lockAtMostFor`를 짧게 (폴링 ~1초, lockAtMostFor 폴링의 2~4배)

sweeper가 타임아웃·REVEAL 전이의 **유일 구동기**이므로, 락 홀더가 죽으면 그 시간만큼 자동 진행이 멈춘다(첫 정답은 이벤트 구동이라 그동안에도 동작). 따라서:

- `@SchedulerLock(lockAtMostFor)`를 30초 잡 선례(`EventPublicationRetryScheduler`의 `PT1M`)대로 복제하면 락 홀더 사망 시 최대 60초 자동 진행 정지. **폴링 ~1초에 맞춰 `lockAtMostFor≈PT4S`**(폴링의 2~4배)로 낮추고 sweep을 짧고 멱등하게(배치 상한) 유지 → 홀더 사망 시 회복이 몇 초로 유계화
- 폴링 주기는 ~1초가 기본(타임아웃 지터 ≤~1초). 더 줄이면 반응이 빨라지나, 단일 replica만 돌므로 부하는 여전히 작다(전이마다 CAS 1회, replica 수와 무관)

## Decision 9 — 채점은 durable winner의 projection, 점수판은 멤버 조건부 ZSET

점수판은 `room:{id}:scores` ZSET(member=playerId, score=누적점수)이다. 라운드마다 첫 정답자에게 `ZINCRBY +1`, 게임 끝에 `ZREVRANGE` 최고자가 우승.

적대 검증이 짚은 두 경합을 닫는다:
- **점수 유실**: CAS==1 직후 점수 outbox INSERT 전에 죽으면 epoch이 전진해 sweeper가 재시도하지 않아 점수가 영구 유실. → 채점을 "CAS 후 1회 발행하는 사이드이펙트"가 아니라 **CAS가 커밋한 durable `winnerId` 필드의 projection**으로 만들고, `(roomId, roundSeq)` 멱등 키로 중복 적용을 흡수
- **퇴장 경합**: 정답 `ZINCRBY`와 퇴장 `ZREM`이 겹치면 떠난 플레이어가 부활하거나 정당한 점수가 소거. → 가점을 **"아직 멤버일 때만"** 원자 조건부로. 사용자 규칙(퇴장자는 점수판에서 제거)에 맞춰 퇴장 시 `ZREM`하되, 그 직후 가점이 부활시키지 못하게 막음

## Decision 10 — 라운드 순서는 게임 시작 시 셔플, 시퀀스를 상태에 고정

`트랙 전부가 라운드`이되 순서는 **게임 시작 시 셔플**한다(플레이리스트 제작자가 순서를 외워 유리해지지 않게). 셔플된 trackId 시퀀스를 `room:{id}:round`의 `trackOrder`에 고정해, 재접속·sweeper 회수가 같은 순서를 본다. `trackIndex`로 현재 위치를 가리킨다. (원순서 고정이 더 단순하지만 공정성에서 셔플이 낫고 비용은 동일 — 셔플로 확정.)

## 동시성·실패 모드 요약 (적대 검증 도출, 구현 시 필수 보정)

| 함정 | 보정 | Decision |
|---|---|---|
| 멱등성을 `withLock`에 걸면 락 만료로 이중 전이·이중 채점·라운드 스킵 | 전이 게이트 = 단일 원자 Lua CAS, 핫패스 락 제거 | 3 |
| 시계 스큐로 조기/즉시 종료 | 앵커·게이트·sweeper 선택 모두 `redis.call('TIME')` | 4 |
| HSET 후 ZADD 전 사망 → ZSET 미등록 영구 고아(STUCK) | phase HSET + deadline ZADD/ZREM 동일 Lua + Hash 화해 백스톱 | 5 |
| 유령 방: 삭제된 방을 sweeper가 끝까지 구동 + outbox poison | 방 삭제/빈 방 시 라운드 teardown, sweeper가 DB 방 부재 시 정리 | 7 |
| 방 벽돌화: 라운드 ENDED ↔ DB status 불일치 | ENDED가 status 멱등 플립, 방장 end도 같은 CAS 경로 | 7 |
| 재접속/폴링으로 OPEN 정답 누출(치팅) | 스냅샷 phase 게이팅, answer-stripped 트랙 목록 | 6 |
| 점수 유실 / 퇴장 시 부활·소거 | 채점=winner projection·`(roomId,seq)` 멱등키, 가점 멤버 조건부 | 9 |
| sweep 홀더 사망 시 자동 진행 정지(sweeper가 주 구동기) | `lockAtMostFor≈PT4S`(폴링×2~4), sweep 짧게·멱등; 첫 정답은 이벤트 구동이라 무관 | 8 |

## 파라미터 — 전부 확정

- **라운드 순서**: **게임 시작 시 셔플**(Decision 10). 시퀀스를 `room:{id}:round`의 `trackOrder`에 고정해 재접속·sweeper 회수가 같은 순서를 본다
- **REVEAL 길이**: **5초 고정**(웹 조사·출처 검증 근거; 차등·호스트 조절 없음). 하한 — 정답 인지 2~3초(Typito)·transient 표준 4초(Material)·동일 구조 binb 5초 / 상한 — Kahoot 10초 "낭비"·Nielsen 10초 한계. `deadline` 버퍼는 2초 유지
- **늦은 정답 수용**: **마감 후 추측 불인정**. 첫 정답 CAS에 `now<=deadline`(Redis TIME) 게이트를 더해, 마감 시각 이후 도착한 정답은 거부한다(sweeper가 닫기 전 OPEN 잔여 창의 늦은 추측도 무효). 해당 라운드는 winner=null로 타임아웃 처리
- **최소 시작 인원**: **현행 유지(`≥1`, 제한 없음)** — 선행 슬라이스와 동일, 연습·테스트 편의. 별도 가드 추가 안 함(스펙 변경 없음)
- **정답 정규화**: **모든 공백 제거 + 대소문자 무시**. 입력·정답 양쪽을 같은 규칙으로 정규화해 비교. 예: 정답 `밤을 달리다` ↔ `밤을 달 리다`·`밤을     달리다`·`밤 을 달리다` 모두 정답. (초성·특수문자 등 추가 정규화는 후속 보류)
