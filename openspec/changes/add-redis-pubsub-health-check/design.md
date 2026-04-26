## Context

현재 백엔드는 도메인 이벤트 broadcast(채팅·입퇴장·세션 교체 등)를 Redis pub/sub 위에서 동작시키고 있다. `back/src/main/kotlin/ilpak/nomat/room/in/RoomEventRedisSubscriber.kt`의 `RedisMessageListenerContainer`가 `room:*:events` 패턴을 P-SUBSCRIBE 하고, `RoomStompController` 및 `RoomEventListener`가 `StringRedisTemplate.convertAndSend()`로 publish 한다. Spring Boot 3.4 기본 클라이언트는 Lettuce이고, Lettuce는 pub/sub용 별도 dedicated connection을 사용한다.

다음의 사일런트 페일이 dev 환경에서 발생했다:
1. 한 인스턴스의 Lettuce dedicated pub/sub connection이 끊겼지만 일반 command connection은 살아 있음
2. Spring Boot Actuator의 기본 `RedisHealthIndicator`는 command connection 대상 PING만 보므로 정상으로 보고함
3. `back/src/main/kotlin/ilpak/nomat/health/in/HealthController.kt`의 `/health` 엔드포인트는 어떤 검사도 없이 하드코딩된 `{"status":"ok"}`를 반환
4. `infra/app/compose.yml`의 Swarm healthcheck는 `/health`만 보므로 컨테이너를 healthy로 유지
5. `infra/app/nginx.conf`의 nginx upstream은 healthy 인스턴스로 계속 트래픽을 보냄
6. WebSocket 세션이 sticky하게 그 깨진 인스턴스에 박힌 사용자는 broadcast 메시지를 영구히 받지 못함
7. 재배포 전까지 자동 회복되지 않으며, 운영자가 알아챌 신호도 없음

제약·이해관계자:
- 배포 단위: Docker Swarm `replicas: 2`, rolling update `start-first`. 배포 중 일시적 unhealthy가 자연스럽게 발생할 수 있음
- 헬스체크 호출자: 1) Swarm 컨테이너 healthcheck (`infra/app/compose.yml:17`), 2) (잠재적) nginx upstream 모니터링, 3) 운영자 수동 확인
- Actuator는 이미 의존성(`back/build.gradle.kts:41`)에 포함됨. 별도 라이브러리 추가 불필요
- 테스트 정책(no mocking): Redis는 Testcontainers로 띄워 실제 round-trip을 검증해야 함
- 코드 품질 정책: 기존 코드 그대로 따라가지 않고 더 나은 구조 제안 가능 — 현재 더미 `/health` 컨트롤러는 의미 있는 검사로 대체할 가치가 있음

## Goals / Non-Goals

**Goals:**
- Redis pub/sub subscription의 살아있음을 self round-trip ping으로 주기 검증한다
- 검증 결과를 Spring Boot Actuator `HealthIndicator`로 표준 통합하여 `/actuator/health` 응답에 노출한다
- 컨테이너 헬스체크가 보는 엔드포인트가 이 검증 결과를 반영하도록 한다 — pub/sub 라운드트립 실패가 실제로 컨테이너를 unhealthy 상태로 만들고 nginx upstream에서 빠지게 한다
- 라운드트립 실패 시 운영자가 원인을 빠르게 짚을 수 있는 진단 로그를 남긴다
- 모든 시나리오를 Testcontainers Redis로 통합 테스트한다 (라운드트립 성공·timeout·subscribe 누락 등)

**Non-Goals:**
- Redis pub/sub subscription의 자동 복구 로직 자체를 추가하는 것은 본 변경의 범위가 아니다 (= unhealthy 신호만 제공). Lettuce keep-alive·`setRecoveryBackoff` 명시화·재구독 강화는 별도 변경에서 다룬다
- Prometheus 메트릭·알림 룰 추가는 별도 변경
- 프론트엔드, 다른 도메인(playlist, player, favoriteplaylist, auth) 변경 없음
- 일반 Redis command connection의 헬스체크는 Actuator가 기본 제공하는 `RedisHealthIndicator`에 위임한다 (중복 구현 안 함)
- Kafka/Elasticsearch/Debezium 등 다른 인프라 컴포넌트의 헬스체크는 본 변경에 포함하지 않는다

## Decisions

### Decision 1: Self round-trip ping 방식으로 subscription 살아있음을 검증한다

자체 publish 후 자체 receive까지의 round-trip을 확인한다. `RedisMessageListenerContainer`의 내부 상태(`isRunning()` 등)를 읽는 방식보다 한 단계 더 강한 보장이다 — Lettuce 커넥션이 "기술적으로 살아있다"고 보고하면서도 실제 메시지가 흐르지 않는 케이스(이번 인시던트의 본질)까지 잡아내기 때문.

**대안 검토:**
- *컨테이너 상태 읽기 (`isRunning()`)*: 본질적으로 이번 장애를 못 잡는다. 기각.
- *외부 능동 ping (다른 클라이언트가 publish하고 backend가 받는 걸 확인)*: 외부 의존성을 만들고 운영 복잡도가 늘어남. 기각.
- *RedisTemplate command connection PING*: Actuator 기본 `RedisHealthIndicator`가 이미 함. 본 변경의 책임이 아님. 기각.

### Decision 2: `infrastructure/redis/` 패키지에 위치, 인스턴스별 고유 채널 사용

신규 컴포넌트는 `back/src/main/kotlin/ilpak/nomat/infrastructure/redis/RedisPubSubHealthIndicator.kt` (가칭)에 위치. 기존 `infrastructure/redis/` 패키지의 횡단 관심사 패턴을 그대로 따른다.

채널 네이밍: `health:pubsub:<instanceId>` 형식. `instanceId`는 Spring Boot Application 시작 시점에 생성한 UUID(또는 기존 `applicationContext.id`). 인스턴스별 고유 채널을 쓰는 이유:
- 동일 채널을 모든 인스턴스가 함께 쓰면 "옆 인스턴스의 publish를 자기가 받아서 healthy로 오판"하는 거짓 양성이 가능. 인스턴스별 고유 채널은 자기 자신의 publish만 받게 보장한다.
- 운영자가 Redis MONITOR나 PUBSUB CHANNELS로 상태를 디버깅할 때 인스턴스 식별이 쉽다.
- Redis 키 영속성 부담 없음 (pub/sub은 stateless).

**대안 검토:**
- *공용 단일 채널 (예: `health:pubsub:ping`)*: 모든 인스턴스가 함께 P-SUBSCRIBE → 본인이 발행한 ping과 남이 발행한 ping을 구분하기 위해 message body에 instanceId가 필요. self-receive 보장이 약해짐. 기각.
- *기존 `room:*:events` 채널 위에 ping payload 추가*: 도메인 이벤트와 헬스 신호가 섞여 디버깅·로깅이 어려워짐. 기각.

### Decision 3: Actuator `HealthIndicator` 인터페이스 구현 + 기존 `/health` 컨트롤러 폐기

신규 컴포넌트는 `org.springframework.boot.actuate.health.HealthIndicator`(또는 `AbstractHealthIndicator`)를 구현한다. 결과는 `/actuator/health` 응답의 `components.redisPubSub` 항목에 자동 노출된다.

**`/health` 엔드포인트 이전:**
- 기존 `back/src/main/kotlin/ilpak/nomat/health/in/HealthController.kt`의 더미 컨트롤러는 **삭제**한다 (의미 없는 always-ok 응답으로 사일런트 페일을 방치한 원인 중 하나).
- Swarm healthcheck(`infra/app/compose.yml:17`)가 호출하는 경로 `/health`를 **유지**하기 위해 `management.endpoints.web.base-path: /` + `management.endpoints.web.path-mapping.health: health` 설정을 application.yml에 추가하여 Actuator의 health endpoint가 `/health`로 노출되게 한다. (대안: Swarm compose.yml의 healthcheck URL을 `/actuator/health`로 변경하는 것도 가능 — 운영 변경 표면적은 작지만 외부 호출자가 더 있을 가능성이 있어 endpoint 경로를 보존하는 쪽을 우선)
- `application.yml`에 `management.endpoint.health.show-components: always` 와 `show-details: always`를 둔다. `redisPubSub` 컴포넌트 상태와 details(`channel`, `latencyMs`, `timeoutMs`, `reason`)가 응답 본문에 노출된다. Redis 내부 접속 주소 노출을 막기 위해 `RedisPubSubHealthIndicator`는 예외 객체를 `Health.Builder.withException(ex)` / `down(ex)`로 그대로 싣지 않고, sanitize된 `reason` 필드만 details에 담는다. 원본 예외는 내부 로그로만 남긴다. (`show-details: when-authorized`는 `/health/**` permitAll + `management.endpoint.health.roles` 미설정 조합에서 익명 호출자에게도 details가 노출되어 의미가 없으므로 채택하지 않는다.)
- `SecurityConfiguration.kt:52`의 `/health/**` permit 규칙은 그대로 두면 신규 endpoint도 자동 적용됨.

**대안 검토:**
- *기존 컨트롤러 유지 + Actuator는 `/actuator/health`에 별도 노출*: endpoint 두 개로 분기되어 일관성↓. 기각.
- *기존 컨트롤러를 두고 `HealthIndicator`를 의존성 주입 받아서 직접 호출*: HealthIndicator 컬렉션·집계·status mapping 로직을 수동으로 다시 짜야 함. 기각.
- *기존 컨트롤러를 그대로 두고 endpoint 매핑만 옮김*: 더미 응답은 그대로라 사일런트 페일 위험 잔존. 기각.

### Decision 4: Round-trip 검증 주기·타임아웃·실패 임계값

- **검증 주기**: Spring Boot Actuator는 healthcheck 호출 시점마다(=Swarm healthcheck 30초 간격) 실행한다. 별도 스케줄러를 두지 않는다. 별도 스케줄러는 단순함을 줄이고 Actuator의 캐시(`management.endpoint.health.cache.time-to-live`)와 결합하는 게 표준적이다.
- **Round-trip 타임아웃**: 기본 **2초**. round-trip이 2초 안에 완료되지 않으면 `Health.down()`. Swarm healthcheck 타임아웃(`infra/app/compose.yml:18` = 10초)보다 충분히 짧음.
- **연속 실패 임계값**: Actuator 단일 호출 결과를 그대로 사용. 단일 호출 실패 = down. Swarm healthcheck 자체에 `retries: 3` (`compose.yml:19`)이 있어 일시적 깜빡임은 거기서 흡수됨 — 이중 그레이스 정책을 만들지 않음.
- **외부화**: `app.health.pubsub.timeout-ms` (기본 2000), `app.health.pubsub.channel-prefix` (기본 `health:pubsub`) 등을 `application.yml`에 노출. 환경별 튜닝 가능.

### Decision 5: 동시 unhealthy 시나리오 그레이스 — 별도 정책 두지 않음

"Redis 자체가 죽으면 모든 인스턴스가 동시에 unhealthy가 되어 서비스 전체가 떨어진다"는 우려가 있을 수 있으나:
- Redis가 죽으면 채팅·세션 기능 자체가 동작 불능이고, nginx가 모든 upstream을 빼서 5xx 응답을 주는 게 운영적으로 더 정확한 신호다 (silent degradation 회피).
- "Redis 절단 시 컨테이너는 살려두고 일부 기능만 마비"는 사용자 체감으로는 더 나쁘다.
- 따라서 별도 그레이스(N개 이상 살리기 등)는 두지 않는다.

## Risks / Trade-offs

- **[Risk] Actuator health endpoint 응답 시간이 round-trip만큼 늘어남** → Mitigation: 기본 2초 타임아웃, Actuator `cache.time-to-live`(예: 10s)로 연속 호출 부하 완화. Swarm healthcheck 30초 주기에 충분히 들어맞음.
- **[Risk] Redis 일시 장애로 모든 replica가 동시에 unhealthy** → Mitigation: 위 Decision 5의 명시적 trade-off로 수용. nginx 5xx가 운영자에게 더 명확한 신호를 줌.
- **[Risk] Lettuce dedicated pub/sub connection을 검증 컴포넌트가 별도로 또 점유** → Mitigation: 기존 `RedisMessageListenerContainer`에 추가 채널을 등록해 동일 dedicated connection을 공유한다. 신규 listener container를 별도로 만들지 않는다.
- **[Risk] 인스턴스별 고유 채널이 Redis client list를 어지럽힘 (replica × restart 마다 신규 채널)** → Mitigation: Pub/sub 채널은 영구 저장이 아님. P-SUBSCRIBE 해제 시 자동 정리됨. 운영 비용 무시 가능.
- **[Risk] `/health` endpoint의 응답 스키마가 `{"status":"ok"}` 단순 형태에서 Actuator의 `{"status":"UP","components":{...}}` 형태로 바뀌어 외부 호출자(있다면)가 깨짐** → Mitigation: Swarm healthcheck는 HTTP 200/non-200만 보므로 영향 없음. 다른 호출자(프론트, 외부 모니터링)가 있는지 코드 검색으로 확인하고, 있으면 본 변경에서 함께 정리. (현재 grep 기준 직접 호출자 없음.)
- **[Trade-off] 검증 컴포넌트가 healthy 판정에만 쓰이고 자동 복구는 안 함** → Non-Goal로 명시. unhealthy 상태가 길어지면 nginx에서 빠지고 Swarm이 컨테이너를 재시작하는 흐름에 의존. 자동 복구는 별도 변경(Lettuce keep-alive·`setRecoveryBackoff`)으로.

## Migration Plan

1. **PR 머지** → CI가 `develop`에서 도커 이미지 빌드 → EC2에 `docker stack deploy` (`infra/app/compose.yml`). 기존 rolling update(`start-first`, `parallelism: 1`) 동작 그대로.
2. **롤아웃 중**: 새 컨테이너가 시작되어 round-trip ping이 성공하면 healthy → 옛 컨테이너 종료. 만약 새 컨테이너가 round-trip 실패하면 healthcheck `start_period: 60s` 안에 정리되지 못해 Swarm이 `failure_action: rollback` 발동 → 자동 롤백.
3. **롤백 전략**: 본 변경은 백엔드 단일 PR이므로 `git revert` 후 동일 파이프라인으로 재배포. 데이터 마이그레이션 없음.
4. **검증**: 배포 후 `curl https://api.dev.nomat.live/health | jq .components.redisPubSub` 로 `status: "UP"` 확인. dev 환경에서 의도적으로 한 인스턴스의 Redis 연결을 끊어(예: `iptables` drop) unhealthy → nginx upstream 제외가 발생하는지 수동 검증. (이 수동 검증은 운영 SOP 문서에 추가하되 자동화는 하지 않음 — 본 변경 범위 외)

## Open Questions

- ~~`management.endpoint.health.show-components: always`로 응답에 컴포넌트 세부 사항을 노출해도 보안상 문제없는지 확인 필요.~~ → 결정: `show-components: always` + `show-details: always`. Redis 내부 접속 주소 등 민감 정보는 `RedisPubSubHealthIndicator` 코드에서 예외 객체를 details에 싣지 않고 sanitize된 `reason` 필드만 담는 방식으로 차단한다 (`builder.down(ex)`/`withException(ex)` 미사용). `show-details: when-authorized`는 `/health/**` permitAll + roles 미설정 조합에서 익명 호출자도 통과하므로 보안 효과가 없어 기각.
- 기존 `HealthResponse`(`HealthController.kt:17`)를 참조하는 외부 호출자가 정말 없는지 grep 외에 운영팀 확인 필요. 있다면 응답 스키마 변경 안내 필요.
- 신규 application.yml 설정값이 dev 프로파일에만 들어가도 충분한지(local/test에서는 검증 컴포넌트가 어떻게 동작해야 하는지) — 통합 테스트가 검증 컴포넌트를 직접 부팅하므로 모든 프로파일에서 활성화하는 쪽이 안전. 기본값은 모든 프로파일에서 동일하게 적용.
