## 1. 백엔드 — 유예 예약 포트 (`room/application/domain`)

- [x] 1.1 `PendingLeaveStore` 포트 인터페이스 추가 — `schedule(roomId, playerId, graceSeconds)`, `remove(roomId, playerId): Boolean`, `findDue(): List<PendingLeave>`, `restore(roomId, playerId)`. KDoc에 "`remove`는 취소와 claim을 겸하며 반환값이 소유권 획득 여부"라는 계약(design Decision 3)과 "시각 앵커는 Redis `TIME`"(Decision 4)을 명시한다
- [x] 1.2 `PendingLeave(roomId: Long, playerId: Long)` 데이터 클래스 추가. score(만료 시각)를 싣지 않는 이유(복원이 `now + 5초`라 원래 score가 필요 없음, Decision 2 재시도 정책)를 KDoc에 남긴다

## 2. 백엔드 — Redis 어댑터 (`room/out`)

- [x] 2.1 `PendingLeaveRedisKeys` 객체 추가 — 단일 키 `rooms:pending-leaves`와 member 포맷 `"{roomId}:{playerId}"`(직렬화·역직렬화 함수)를 한 곳이 소유한다. `RoundRedisKeys`처럼 `internal`로 두어 화이트박스 테스트가 같은 키를 계산할 수 있게 한다. 샤딩하지 않는 이유(단일 키 연산뿐이라 `CROSSSLOT` 무관)를 KDoc에 적는다
- [x] 2.2 `PendingLeaveStoreImpl` 추가 — **`private class` 가시성 유지**. Lua 스크립트 네 개: `SCHEDULE`(`TIME` → `ZADD key now+grace member`), `REMOVE`(`ZREM`, 반환 1/0), `FIND_DUE`(`TIME` → `ZRANGEBYSCORE key -inf now`, member만), `RESTORE`(`TIME` → `ZADD key now+RETRY_DELAY_MS member`). `NOW_MS` 프리앰블은 `RoundStateStoreImpl`의 것과 동일 문자열을 쓴다(공유 상수로 추출해도 좋다)
- [x] 2.3 `RETRY_DELAY_MS = 5_000` 상수와 그 근거(매 틱 재시도 시 로그 폭주 방지, 시간 기반 GC 불채택)를 주석으로 남긴다

## 3. 백엔드 — 서비스 (`room/application`)

- [x] 3.1 `RoomService`에 `scheduleLeave(roomId, playerId)` 추가 — `@Value("\${app.room.reconnect-grace-period-seconds:60}")`를 `ReconnectGracePeriodManager`에서 옮겨 와 `pendingLeaveStore.schedule`을 호출한다. 예약 로그(`info`)를 남긴다
- [x] 3.2 `RoomService`에 `cancelPendingLeave(roomId, playerId): Boolean` 추가 — `pendingLeaveStore.remove`를 그대로 위임하고 true면 "재접속으로 유예 취소" 로그를 남긴다
- [x] 3.3 `RoomService`에 `sweepDueLeaves()` 추가 — `findDue()` 순회, 항목마다 `remove`로 claim(0이면 건너뜀) → `leave(roomId, playerId)` → 예외 시 `warn` 로그(roomId·playerId·원인) 후 `restore`. 정상 반환은 완료로 간주하고 성공 로그(`info`, "유예 시간 만료로 퇴장 처리")를 남긴다. 한 항목의 실패가 다음 항목 처리를 막지 않도록 항목 단위로 예외를 잡는다
- [x] 3.4 `leave(roomId, playerId)`의 시그니처·동작은 바꾸지 않는다 — 방 없음·멤버 아님이 조용한 no-op이라는 현재 성질이 sweeper의 "예외 없으면 완료" 판별의 근거임을 KDoc에 명시한다

## 4. 백엔드 — 인바운드 어댑터 (`room/in`)

- [x] 4.1 `RoomDisconnectListener`의 `reconnectGracePeriodManager.scheduleLeave` 호출을 `roomService.scheduleLeave`로 교체하고 `ReconnectGracePeriodManager` 의존을 제거한다. `activeSessionManager.removeSession` 가드는 그대로 둔다. **`private class` 가시성 유지**
- [x] 4.2 `PendingLeaveSweeper` 추가 — **`private class`**, `@Scheduled(fixedDelay = 1_000)` + `@SchedulerLock(name = "pending-leave-sweep", lockAtMostFor = "PT4S")`로 `roomService.sweepDueLeaves()` 호출. KDoc에 `RoundDeadlineSweeper`와 같은 구조임과, ShedLock이 4초 뒤 풀려 다른 replica가 겹쳐 돌아도 claim-then-act 덕에 안전하다는 점(락은 부하 분산 장치)을 적는다

## 5. 백엔드 — 웹 계층·설정 (`infrastructure/web`, `application.yml`)

- [x] 5.1 `RoomJoinChannelInterceptor`의 세 갈래를 `roomService.cancelPendingLeave`로 교체 — (a) 같은 방 세션: 반환값 무시, (b) 다른 방 세션: 옛 방 `cancelPendingLeave` → `leave` → `join`, (c) 세션 없음: `cancelPendingLeave`가 true면 통과, false면 `join`. `ReconnectGracePeriodManager` 의존을 제거한다
- [x] 5.2 `infrastructure/web/ReconnectGracePeriodManager.kt` 삭제. 참조가 남지 않았는지 컴파일로 확인한다
- [x] 5.3 `@Scheduled` 전용 스케줄러 빈을 이름 `taskScheduler`로 명시 선언 — `infrastructure/events`(기존 `ShedLockConfiguration` 옆) 또는 새 `SchedulingConfiguration`에 `@Bean(name = "taskScheduler") fun taskScheduler(builder: ThreadPoolTaskSchedulerBuilder): ThreadPoolTaskScheduler = builder.build()`. KDoc에 "`@EnableWebSocketMessageBroker`의 `messageBrokerTaskScheduler` 때문에 Boot 자동설정이 꺼져 있고, `TaskScheduler` 빈이 여럿일 때 `@Scheduled`는 이름 `taskScheduler`를 잡는다"는 해석 규칙(design Decision 2)을 남긴다
- [x] 5.4 `WebSocketConfiguration`에 하트비트 설정 — 하트비트 전용 `ThreadPoolTaskScheduler` 빈을 **이름 `wsHeartbeatTaskScheduler`**(스레드 1, 스레드명 접두사 `ws-heartbeat-`)로 추가하고 `enableSimpleBroker("/topic").setHeartbeatValue(longArrayOf(10_000, 10_000)).setTaskScheduler(...)`로 연결한다. 이름을 `taskScheduler`로 두면 안 되는 이유(5.3의 해석 규칙)와 `messageBrokerTaskScheduler` 재사용을 택하지 않은 이유(순환 참조)를 주석으로 남긴다
- [x] 5.5 `application.yml` 공통 영역에 `server.shutdown: graceful`, `spring.lifecycle.timeout-per-shutdown-phase: 20s`, `spring.task.scheduling.pool.size: 4`, `spring.task.scheduling.thread-name-prefix: scheduling-` 추가. 프로파일별 오버라이드 영역(local/test/dev)이 `spring.task`를 재정의해 누락되지 않는지 확인한다

## 6. 백엔드 — 테스트

기존 통합 테스트(`RoomLeaveIntegrationTest`, `RoomSessionReplaceIntegrationTest`, `RoomGameSessionIntegrationTest`, `RoundStateStoreIntegrationTest`)의 구조·`@IntegrationTest` + Testcontainers·`await` 패턴을 먼저 읽고 동일한 방식으로 작성한다. **모킹 라이브러리를 도입하지 않는다.** 유예는 기존대로 `app.room.reconnect-grace-period-seconds=2`를 쓴다.

- [x] 6.1 기존 세 통합 테스트의 유예 만료 대기를 sweeper 주기에 맞춰 조정 — `Thread.sleep(3000)` 고정 대기 대신 `await().atMost(Duration.ofSeconds(6))`로 `LEFT` 이벤트/멤버십 변화를 기다리고, "퇴장되지 않아야 한다" 검증은 유예 + 폴링 주기 + 여유(≈4초) 동안 `LEFT`가 없음을 확인한다. `RoomGameSessionIntegrationTest`의 게임 중 재접속 케이스에는 "서비스로 예약을 넣고(다른 인스턴스가 기록한 상황) STOMP CONNECT로 취소된다"는 단언을 한 줄 덧붙여 `room-game-session` MODIFIED의 신규 시나리오를 직접 덮는다
- [x] 6.2 `PendingLeaveStoreIntegrationTest` 추가 — `schedule` 후 `findDue`가 만료 전엔 비어 있고 만료 후 항목을 돌려주는지, `remove`가 1/0을 올바르게 돌려주는지, `restore` 후 항목이 약 5초 뒤 다시 due가 되는지(Redis TIME 기준)
- [x] 6.3 다른 인스턴스 취소 테스트 — `RoomService.scheduleLeave` 후 `cancelPendingLeave`가 true를 돌려주고, 이후 유예가 지나도 멤버가 남아 있는지. 두 인스턴스는 같은 Redis를 보므로 서비스 호출 두 번으로 재현한다(STOMP 두 인스턴스를 띄우지 않는다)
- [x] 6.4 퇴장 실패 재시도 테스트 — 테스트가 `room:{id}:lock` 키를 선점한 상태로 유예를 만료시켜 `ConflictException`이 나게 하고, 항목이 `rooms:pending-leaves`에 복원돼 남아 있는지 확인한 뒤 락을 지우면 5초 남짓 뒤 퇴장이 완료되는지 `await`로 검증한다
- [x] 6.5 멱등 완료 테스트 — 존재하지 않는 roomId로 `schedule`한 항목이 만료되면 예외 없이 항목이 사라지고 재시도되지 않는지
- [x] 6.6 경합 테스트 — 만료 항목을 sweeper가 조회한 직후 `cancelPendingLeave`로 먼저 claim하면 sweeper가 `leave`를 실행하지 않는지(`sweepDueLeaves` 직접 호출로 순서를 통제). 멤버가 남아 있고 `LEFT`가 방송되지 않아야 한다
- [x] 6.7 종료 시 예약 기록 테스트 — STOMP 접속 후 `subProtocolWebSocketHandler` 빈(`SmartLifecycle`)을 직접 `stop()`해 세션이 닫히고 ZSET에 항목이 생기는지 확인한 뒤 `start()`로 복구한다. (`context.close()` 방식은 Testcontainers가 컨텍스트 빈이라 같은 JVM의 뒤 테스트를 깨뜨리고, `@IntegrationTest`의 명시적 `classes` 때문에 중첩 `@TestConfiguration` 프로브가 등록되지 않아 폐기)
- [x] 6.8 하트비트 미수신 테스트(`@Tag("slow")`) — `StandardWebSocketClient`로 raw 세션을 열어 JWT 쿠키로 핸드셰이크하고 `CONNECT` 프레임 텍스트(`accept-version:1.2`, `heart-beat:10000,10000`, `roomId`)를 직접 보낸 뒤 침묵. 약 35초 이내에 서버가 세션을 닫고 `rooms:pending-leaves`에 항목이 생기는지 `await`로 검증한다. `@Tag("slow")`는 표식일 뿐이며(`build.gradle.kts`에 태그 필터가 없어 CI에서도 실행된다) 제외 설정은 이 change에서 하지 않는다. `WebSocketStompClient`는 `heart-beat` 헤더가 있으면 `TaskScheduler`를 강제하므로 쓰지 않는다는 주석을 남긴다
- [x] 6.9 스케줄러 격리 테스트 — `ApplicationContext`에서 이름 `taskScheduler` 빈이 `ThreadPoolTaskScheduler`이고 풀 크기가 4인지, 그리고 `PendingLeaveSweeper`가 실행된 스레드명이 `scheduling-`로 시작하는지(sweep 안에서 `Thread.currentThread().name`을 로그로 남기거나 테스트용 훅으로 확인) 검증한다. 단일 스레드 폴백이면 스레드명이 `pool-N-thread-1` 형태라 구분된다
- [x] 6.10 하트비트 협상 회귀 확인 — 기존 `connectStomp` 헬퍼(`defaultHeartbeat = {0,0}`)로 접속하는 모든 테스트가 그대로 통과하는지(서버 하트비트를 켜도 `0,0` 클라이언트는 협상 결과 비활성)
- [x] 6.11 `./gradlew test` 전체 통과
- [x] 6.12 `./gradlew detekt` 실행 후 신규 경고 0건 확인 — 신규는 `PendingLeaveRedisKeys.parseMember`의 `ReturnCount` 1건이었고 해소. 남은 `RoomService` `LongParameterList`(develop에서 이미 7개로 기준선에 포함)와 `PendingLeaveSweeper` `UnusedPrivateClass`(기존 `RoundDeadlineSweeper`와 같은 `@Component` private class 패턴, detekt 설정이 `@RestController`·`@Repository`만 무시)는 기존 경고와 같은 범주

## 7. 인프라 (`infra/app`)

- [x] 7.1 `compose.yml`의 `spring-app` 서비스에 `stop_grace_period: 30s` 추가. `server.shutdown` 페이즈 타임아웃 20초보다 길어야 하는 이유를 주석으로 남긴다. compose 본문 변경이라 config 키 버전 올림은 해당 없음(nginx.conf·alloy 미변경)
- [ ] 7.2 infra-push-develop 워크플로로 배포된 뒤 `docker service inspect nomat-back_spring-app`에서 `StopGracePeriod`가 30s로 반영됐는지 확인한다

## 8. 배포·운영

- [ ] 8.1 백엔드 배포(back-push-develop) 완료 후 dev MySQL에서 배포 시각 이전 `created_date`의 `ACTIVE` 방 중 접속자 없는 방을 조회해 `room_entry` → `room` 순으로 수동 삭제한다. 삭제 전 대상 목록을 확인한다
- [ ] 8.2 배포 후 Grafana Loki에서 "유예 시간 만료로 퇴장 처리" 로그가 sweeper 경로(`PendingLeaveSweeper` 스레드)에서 남는지, `warn` 재시도 로그가 반복되는 방이 없는지 확인한다
- [ ] 8.3 후속 change `add-client-auto-reconnect`를 생성하고 design.md의 "후속 change 인계" 섹션 내용을 그 proposal의 출발점으로 옮긴다. 프론트 재접속은 반드시 본 change 배포 이후에 배포한다는 순서를 그 proposal에 명시한다
