## 1. 백엔드 — 설계 검증 및 사전 정리

- [x] 1.1 `back/build.gradle.kts`에서 `spring-boot-starter-actuator` 의존성이 활성화 상태인지 확인 (이미 포함되어 있어야 함)
- [x] 1.2 `back/src/main/kotlin/ilpak/nomat/health/in/HealthController.kt`(더미 always-ok 컨트롤러) 삭제 사전 영향 분석 — 코드베이스에서 `HealthController` / `HealthResponse` 직접 참조처가 없는지 grep으로 최종 확인
- [x] 1.3 `back/src/main/resources/application.yml`에 추가할 `management` 설정의 dev/local/test 프로파일별 영향을 정리 (모든 프로파일 공통 적용 방침 확정)

## 2. 백엔드 — Pub/Sub Health 컴포넌트 구현

- [x] 2.1 `back/src/main/kotlin/ilpak/nomat/infrastructure/redis/RedisPubSubHealthIndicator.kt` 신설 — `org.springframework.boot.actuate.health.AbstractHealthIndicator` 구현. `private class`로 작성하여 패키지 외부 노출 금지
- [x] 2.2 인스턴스 고유 식별자(`instanceId`)를 컴포넌트 생성 시 한 번 발급 (UUID 또는 `applicationContext.id` 기반)
- [x] 2.3 채널 이름 규칙 `<channelPrefix>:<instanceId>` 구현. 기본 prefix `health:pubsub`
- [x] 2.4 `health()` 호출 시 round-trip 로직 구현: 고유 payload(예: nano-time + UUID) 발행 → CountDownLatch 또는 CompletableFuture로 timeout 대기 → 일치 payload 수신 시 UP, 미수신 시 DOWN
- [x] 2.5 round-trip 결과 details(`channel`, `latencyMs` 또는 `timeoutMs`, 실패 사유)를 `Health` 빌더에 채워 넣기
- [x] 2.6 실패 시 WARN 레벨 로그 한 줄 출력. 매 호출마다 stack trace 폭증을 막도록 message 위주 (예외가 있다면 메시지만, stack trace는 첫 발생 시 또는 toggle)
- [x] 2.7 Redis 발행 자체가 예외(connection refused 등)를 던질 때 `Health.down(ex)`로 변환

## 3. 백엔드 — 기존 RedisMessageListenerContainer와 통합

- [x] 3.1 `back/src/main/kotlin/ilpak/nomat/room/in/RoomEventRedisSubscriber.kt`의 `RoomEventRedisSubscriberConfiguration` 또는 신규 `RedisListenerContainerConfiguration`로 컨테이너 생성을 일원화 — 도메인 이벤트 listener와 헬스 ping listener를 동일 `RedisMessageListenerContainer`에 등록
- [x] 3.2 헬스 컴포넌트가 자기 자신의 채널(`<prefix>:<instanceId>`)에 P-SUBSCRIBE 되도록 컨테이너에 추가 등록
- [x] 3.3 동일 인스턴스만 자기 ping을 받게 보장하기 위해 listener 내부에서 instanceId 비교(혹은 채널이 인스턴스 고유라 자동 보장됨을 확인하는 주석/테스트)

## 4. 백엔드 — 설정 외부화

- [x] 4.1 `back/src/main/kotlin/ilpak/nomat/infrastructure/redis/RedisPubSubHealthProperties.kt` 신설 — `@ConfigurationProperties(prefix = "app.health.pubsub")`로 `timeoutMs`(기본 2000), `channelPrefix`(기본 `health:pubsub`) 노출
- [x] 4.2 `back/src/main/resources/application.yml`(local/test 공통 영역)과 dev 프로파일에 동일 기본값 명시 — 운영자가 인지 가능하게
- [x] 4.3 `application.yml`에 `management.endpoints.web.path-mapping.health: health` 추가하여 `/health` 경로에서 Actuator health endpoint 제공. (필요 시 `management.endpoints.web.base-path: /` 함께 적용)
- [x] 4.4 `application.yml`에 `management.endpoint.health.show-components: always`(또는 `when-authorized` 정책 결정 후 적용) 추가
- [x] 4.5 `application.yml`에 `management.endpoint.health.cache.time-to-live: 10s` 추가하여 healthcheck 폭증 부하 완화

## 5. 백엔드 — 더미 컨트롤러 제거

- [x] 5.1 `back/src/main/kotlin/ilpak/nomat/health/in/HealthController.kt` 파일 삭제
- [x] 5.2 `health` 패키지가 비게 되면 패키지 디렉토리도 함께 정리
- [x] 5.3 `SecurityConfiguration.kt:52`의 permittedUrls에 `/health/**`가 포함되어 있는지 확인 (현재 포함되어 있어 별도 변경 불필요해야 함)

## 6. 백엔드 — 테스트 (no mocking, Testcontainers)

- [x] 6.1 `back/src/test/kotlin/ilpak/nomat/infrastructure/redis/RedisPubSubHealthIndicatorTest.kt` 신설 — `@IntegrationTest` 적용, Testcontainers Redis 사용
- [x] 6.2 [정상 round-trip] 컴포넌트 호출 시 `Health.up()` 반환, details에 `latencyMs` 포함 검증
- [x] 6.3 [Redis 정지] Testcontainers Redis 컨테이너 stop 후 호출 시 `Health.down()` 반환 검증
- [x] 6.4 [타임아웃] 인위적으로 listener 등록을 차단하거나 짧은 타임아웃을 강제 주입하여 round-trip 실패 시 `Health.down()` + details 사유 검증
- [x] 6.5 [인스턴스 격리] 두 개의 ApplicationContext를 띄워 서로 다른 instanceId를 가진 두 인스턴스가 같은 Redis를 공유할 때 cross-contamination 없이 각자 자기 ping만 받음을 검증 (가능하면 단일 컨텍스트 + 두 컴포넌트 인스턴스 수동 구성으로 단순화)
- [x] 6.6 `back/src/test/kotlin/ilpak/nomat/health/in/HealthEndpointIntegrationTest.kt` (혹은 동일 패키지 갱신) — `WebTestClient`로 `/health` 호출 시 응답 JSON에 `components.redisPubSub.status`가 존재하고 정상 시 200을 반환함을 검증
- [x] 6.7 [DOWN 상태 전파] 헬스 컴포넌트가 강제로 DOWN을 반환하도록 한 상황에서 `/health` 호출이 HTTP 503을 반환함을 검증 (테스트 전용 HealthIndicator를 추가하지 말고, 실제 컴포넌트를 Redis 정지 상태로 떨어뜨려 검증)

## 7. 백엔드 — 빌드·정적분석

- [x] 7.1 `./gradlew test` 실행하여 전체 테스트 통과 확인 (사전부터 존재하던 CDC 동기화 flake `PlaylistControllerTest.searchByTitle`은 단독 실행 시 통과 — 본 변경과 무관)
- [x] 7.2 `./gradlew detekt` 실행하여 신규 코드 정적 분석 통과 확인 (ignoreFailures=true이지만 신규 위반은 제거) — detekt 1.23.3이 JDK 21 jvm-target을 거부하는 사전 환경 이슈로 invocation 실패. 이는 본 변경 이전에도 존재하며 `build.gradle.kts:99`에서 `check` task에 detekt가 묶이지 않도록 이미 분리되어 있음. 신규 Kotlin 파일은 검토 결과 위반 없음
- [x] 7.3 `./gradlew build` 실행하여 최종 빌드 통과 확인

## 8. 인프라·운영 검증 (코드 변경 없음, 배포 후 수동)

- [x] 8.1 dev 배포 후 `curl https://api.dev.nomat.live/health | jq` 결과에 `components.redisPubSub.status: "UP"`이 노출되는지 확인
- [x] 8.2 의도적으로 한 인스턴스의 Redis 연결을 차단(`docker exec` + `iptables` 등)하여 해당 인스턴스가 503을 반환하고 nginx upstream에서 자동 제외되는지 수동 검증. 검증 후 즉시 원복
- [x] 8.3 `infra/app/compose.yml`의 healthcheck `test` 명령은 변경 불필요 — `/health` 경로 그대로 유지됨을 확인. (변경이 발생했다면 Swarm config key 증가 룰 준수)

## 9. 문서·후속

- [x] 9.1 `back/CLAUDE.md` 또는 `infra/CLAUDE.md`에 새 `/health` 응답 스키마 변경 사항을 한 줄 추가 (Actuator health 응답 형태로 변경됨)
- [x] 9.2 design.md의 "Open Questions" 항목 중 응답 스키마 외부 호출자 확인 결과를 PR 설명에 기록
- [x] 9.3 (Non-goal로 분리한) Lettuce keep-alive·`setRecoveryBackoff` 강화는 별도 OpenSpec change로 후속 제안한다는 메모를 PR 설명에 남기기
