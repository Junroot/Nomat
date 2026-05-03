## Why

Dev 환경에서 일부 사용자가 채팅·입퇴장 같은 실시간 broadcast 메시지를 받지 못하는 잠복성 장애가 발생했고, 재배포로만 일시적으로 복구되었다. 원인은 `RedisMessageListenerContainer`(`back/src/main/kotlin/ilpak/nomat/room/in/RoomEventRedisSubscriber.kt`)가 사용하는 Lettuce dedicated pub/sub connection이 idle timeout·네트워크 blip 등으로 조용히 끊긴 뒤 재구독되지 않는 케이스다. 기존 Spring Boot Actuator의 `RedisHealthIndicator`는 일반 command 커넥션에 PING만 보내므로 pub/sub subscription의 단절을 감지하지 못하고, 단순 HTTP `/health`만 보는 Docker Swarm·nginx 헬스체크는 컨테이너를 healthy로 유지해 트래픽이 끊긴 인스턴스로 계속 라우팅된다. 사용자 경험을 망치고 원인 추적을 어렵게 만드는 사일런트 페일을 제거하기 위해 pub/sub round-trip을 검증하는 헬스체크가 필요하다.

## What Changes

- `RedisMessageListenerContainer`에 등록된 pub/sub subscription의 라운드트립 가능성을 검증하는 백엔드 헬스 컴포넌트 도입 (self-publish/self-receive 방식)
- Spring Boot Actuator `HealthIndicator`로 통합되어 `/actuator/health` 응답에 `redisPubSub` 항목으로 노출
- Docker Swarm 컨테이너 헬스체크 엔드포인트(현재 `/health`)가 이 신규 indicator 결과를 포함하도록 endpoint 노출 정책 정리. pub/sub 라운드트립 실패 시 `503` 응답을 받아 Swarm이 컨테이너를 unhealthy로 마킹 → nginx upstream에서 자동 제외
- 라운드트립 실패 시 INFO/WARN 레벨로 의미 있는 진단 로그 출력 (단순 stack trace가 아닌 "기대한 ping을 N ms 안에 받지 못함" 같은 형태)
- 검증 주기·타임아웃·실패 임계값은 설정값으로 외부화 (기본값은 design 단계에서 확정)

## Capabilities

### New Capabilities
- `redis-pubsub-health`: Redis pub/sub subscription의 살아있음을 self round-trip ping으로 검증하고, 그 결과를 Spring Boot Actuator 헬스체크와 컨테이너 헬스체크 엔드포인트에 노출하는 능력

### Modified Capabilities
<!-- 기존 spec 중 요구사항이 바뀌는 capability 없음. (현재 openspec/specs/에는 pr-auto-review 한 건만 존재하며 본 변경과 무관) -->

## Impact

- **서브프로젝트**: back/ 만 영향. front/, infra/ 코드 변경 없음 (단, `infra/app/compose.yml`의 healthcheck는 기존 `/health` 경로를 그대로 쓰므로 운영 동작은 자동 강화됨)
- **도메인 모듈**: 신규 횡단 관심사이므로 `infrastructure/redis/` 또는 `infrastructure/web/` 하위에 위치. 기존 도메인 모듈(playlist/room/player/favoriteplaylist/auth) 코드는 변경 없음
- **헥사고날 계층**: 신규 컴포넌트는 `infrastructure/`에 위치하는 횡단 관심사. `application/` 계층 변경 없음. (`in/` 어댑터로 분류할 정도의 외부 진입점은 아니지만 실제로 actuator endpoint를 통해 외부에 노출되는 신호임을 design에서 정리)
- **APIs**: `/actuator/health`(또는 `/health`) 응답 JSON에 `components.redisPubSub` 항목 신규 추가. 기존 호출자(Swarm healthcheck, 운영 모니터링)와 호환되며 추가 항목이므로 비파괴적 변경
- **의존성**: 신규 라이브러리 추가 없음. Spring Boot Actuator는 이미 사용 중이라고 가정하되 미포함이라면 `spring-boot-starter-actuator`를 의존성에 추가 (design에서 현 상태 확인 후 결정)
- **DB 스키마·ES 매핑·Kafka 토픽**: 영향 없음
- **Redis 키**: pub/sub 채널만 사용하며 영구 키를 만들지 않는다. 헬스 ping용 채널 네임스페이스(예: `health:pubsub:<instanceId>`)를 신규 사용. design에서 채널명 컨벤션·충돌 회피 정책을 정의
- **운영 동작**: pub/sub round-trip이 실패하는 인스턴스가 컨테이너 헬스체크 실패로 nginx upstream에서 자동 제외 → 사일런트 페일 제거. 동시에 모든 인스턴스가 동시에 unhealthy로 떨어지는 시나리오를 막기 위한 그레이스 정책은 design에서 다룸
