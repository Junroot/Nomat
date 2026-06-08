# Design — configure-grafana-alerting

## Decision 1: 알림 정의는 클릭옵스(UI 원천) + git 스냅샷, IaC 미도입

**결정**: 알림 규칙·contact point·notification policy는 Grafana Cloud UI에서 정의한다. git에는 export한 스냅샷만 둔다 (Terraform/Mimir ruler 등 코드형 프로비저닝 미도입).

**근거**:
- 대시보드 3종이 이미 동일 패턴(UI에서 작성 → `infra/grafana/`에 JSON export). 알림만 다른 방식을 쓰면 운영 일관성이 깨진다.
- 규모가 작다(2 replica, `env=dev` 단일 환경). Terraform grafana provider 도입 비용(provider·API token·tfstate 관리)이 이득을 초과한다.
- Grafana-managed 알림은 Loki(로그)와 Mimir(메트릭)를 한 룰에서 쿼리하고 contact point·정책을 한곳에서 관리 — 운영 동선이 짧다.

**트레이드오프**: UI가 원천이므로 git이 **드리프트**할 수 있다(UI에서 바꾸고 export를 깜빡하면 불일치). 재-export는 수동. 이를 README에 명시하고, 향후 알림 수가 늘거나 리뷰 게이트가 필요해지면 Terraform/file provisioning으로 전환하는 경로를 남긴다.

**대안**: Mimir ruler(YAML+CI) — 기존 CI 배포 패턴에 붙지만 **메트릭 전용**이라 Loki 로그 기반 알림(ERROR 급증)을 못 만들고 contact point를 별도 구성해야 해 일원화 이점이 사라진다. 기각.

## Decision 2: 알림은 대시보드 패널을 미러링한다 (보는 것 = 울리는 것)

**결정**: 각 알림은 대시보드의 **actionable 패널**에 1:1로 대응한다. 알림 쿼리는 패널 쿼리를 그대로 쓰고, 임계는 패널의 red zone(패널에 임계가 없으면 운영 합의값)을 쓴다. 알림과 대시보드는 같은 식·같은 라벨을 공유한다.

**근거**: "보는 것 = 울리는 것"의 일관성. 알림이 울리면 볼 패널이 자명하고, 알림용·대시보드용 멘탈 모델이 따로 놀지 않는다. 패널이 곧 runbook이 된다. 솔로/소규모 운영에서 인지 부하를 크게 줄인다.

**범위(이 변경의 결정)**: **세 대시보드(spring-app · node-exporter · nomat-log)의 actionable 패널 전부**를 미러링한다. 단:
- **순수 관측 패널은 제외** — 임계가 없거나 추세만 보는 패널(Request rate, Non-Heap 상세, Threads/Thread states, Load average, Classes loaded/delta, GC collection rate·pause duration·Allocated/Promoted, Hikari Connections·Acquire/usage, Tomcat sessions, Uptime(seconds) 등)은 알림을 만들지 않는다.
- **node-exporter는 커뮤니티 143패널 키친싱크**라 전부 미러링하면 알림 지옥이 된다. 디스크(Filesystem Available, Filesystem ReadOnly/Error)·메모리(Memory available)·CPU(CPU Busy) **핵심 패널만 선별**한다.

**사각지대 보정(중요)**: "앱 완전 다운"은 어느 패널에도 없다(Uptime은 `process_uptime_seconds`라 죽으면 값이 사라질 뿐). 미러링은 패널에서 알림을 파생하므로 **이대로면 가장 중요한 알림이 생성되지 않는다.** 따라서 spring-app 대시보드에 **`Up / replicas` 패널(`sum(up{job="spring-app"})`)을 신설**하고 그것을 미러링한다 — 앱다운을 예외가 아니라 *미러*로 덮는다.

## Decision 3: 롤링 배포 오탐 방지

**맥락**: app 스택은 `update_config: order=start-first, parallelism=1`로 무중단 배포한다. 배포 중 한 replica가 교체되며, 새 컨테이너는 **다른 `instance`(컨테이너 이름) 라벨**로 등장하고 옛 series는 사라진다.

**결정**:
1. **앱 다운은 `sum(up)==0`으로만** 판단한다 (per-replica `up==0` 금지). start-first라 배포 중에도 최소 1 replica가 살아있어 합이 0이 되지 않는다.
2. **per-replica 룰**(GC·CPU·Hikari·Tomcat)은 `for`를 배포 윈도(약 replica당 60–70s × 2)보다 길게 잡는다(≥3m, 대개 10–15m).
3. **NoData 동작**: `up`/`absent` 계열만 `Alerting`(눈머는 게 위험). per-replica·노드 자원 룰은 `OK` — 배포로 series가 잠깐 사라지는 것을 알림으로 보지 않는다. 노드 관측 두절은 별도 `absent(up{job="node-exporter"})`로 커버한다.

## Decision 4: 5xx는 "Error ratio (5xx)" 패널을 미러링하되 저트래픽 가드 추가

**맥락**: `env=dev` 단일 환경으로 요청량이 적다. 요청 3건 중 1건이 5xx면 비율은 33%로 튄다.

**결정**: spring-app "Error ratio (5xx)" 패널의 ratio 쿼리를 알림으로 미러링한다. 단 저트래픽 노이즈를 막기 위해 **트래픽 바닥 가드**(전체 요청 rate `> 0.1`)를 AND로 결합한다 — 패널을 미러링하되 소수 요청에 의한 오탐만 차단한다. 시작 임계 ratio `> 0.05`(검증 패널을 보며 튜닝).

**보조**: nomat-log "ERROR count" 패널은 ERROR 로그 급증 알림으로 미러링하되, 패널의 red step(≥1)을 그대로 쓰면 너무 잦으므로 `increase[5m] > 5`로 둔다.

## Decision 5: 응답시간 알림은 패널대로 평균/max만 (percentile 제외)

`application-metrics`에서 `percentiles-histogram.http.server.requests: false`로 카디널리티를 가드하므로 `_bucket`이 없다 → `histogram_quantile`로 p95/p99 불가. spring-app 대시보드도 "Avg latency"·"Max latency"만 제공한다. 미러링 원칙에 따라 알림도 동일하게 **"Max latency by URI" 패널을 미러링**한다(warning, 시작 임계 예: 2s). percentile이 필요해지면 특정 URI만 히스토그램을 켜는 별도 변경으로 — 10k active series 예산 트레이드오프 동반.

## Decision 6: Discord 2채널 라우팅

**결정**: contact point를 심각도별 2개로 분리한다 — `#nomat-critical`(@here 멘션, `group_wait` 0s, `repeat` 1h), `#nomat-warning`(멘션 없음, `group_wait` 30s, `repeat` 6h). 각 알림 규칙에 `severity=critical|warning` 라벨을 부여해 notification policy가 라우팅한다.

**근거**: 프로젝트가 이미 Discord OAuth2를 사용하므로 운영자의 상시 채널이다. critical/warning을 한 채널에 섞으면 멘션 피로 또는 critical 누락이 생긴다.

## Decision 7: Heap 알림은 패널과 동일하게 클러스터 단위 (미러링 일관성)

spring-app "Heap used %" 패널은 instance 합산(`sum(used{area=heap})*100/sum(max{area=heap})`)으로 전체 추세를 본다. **미러링 원칙에 따라 알림도 동일하게 클러스터 단위로 평가**한다(`sum(used)/sum(max) > 0.9`). 한 replica만 누수되는 상황은 합산값이 임계를 넘을 때까지 가려질 수 있으나, 이는 미러링의 일관성(패널 식 = 알림 식)을 우선한 의도적 선택이다. replica별 포착이 필요해지면 **패널 자체를 `by(instance)`로 바꾼 뒤** 그것을 미러링한다.

## Decision 8: 알림 엔진별 라벨 대소문자 주의

ERROR 로그 알림은 두 경로로 만들 수 있다:
- **메트릭(Mimir)**: `logback_events_total{level="error"}` — Micrometer 컨벤션상 **소문자**
- **로그(Loki)**: `{app="nomat-back", level="ERROR"}` — logback level 라벨 **대문자**

nomat-log 대시보드의 ERROR 패널은 Loki(`level="ERROR"`)를 쓰지만, spring-app "Log events" 패널과 트리거 안정성을 위해 알림 트리거는 **메트릭 경로**(`level="error"`)로 둔다. 원인 추적은 nomat-log Loki 쿼리로. 엔진을 바꿀 때 대소문자 불일치가 흔한 함정이므로 카탈로그에 병기한다.
