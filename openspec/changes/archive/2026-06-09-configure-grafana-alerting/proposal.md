## Why

`observability-pipeline`(Alloy → Grafana Cloud Loki/Mimir)와 `application-metrics`(JVM/Spring `/prometheus` 노출)로 로그·메트릭이 Grafana Cloud로 수집되고, 운영자가 직접 만든 대시보드 3종이 구축되어 있다:

- **spring-app** — JVM / HTTP(Spring MVC) / HikariCP / Tomcat / GC (직접 큐레이션)
- **node-exporter** — 커뮤니티 Node Exporter Full (노드 CPU/메모리/디스크/네트워크)
- **nomat-log** — Loki 로그 조회 (app/access 로그, level별 집계, ERROR 추적)

그러나 **능동적 통지(알림)가 없어** 운영자가 대시보드를 직접 들여다봐야만 장애를 인지한다. 이는 두 가지 구체적 공백을 만든다:

1. **사각지대**: 어느 대시보드에도 `up{job="spring-app"}` 패널이 없다. spring-app의 "Uptime" 패널은 `process_uptime_seconds`라 **앱이 죽으면 값이 사라질 뿐 죽었다는 사실을 통지하지 못한다**. 죽은 앱의 대시보드는 빈 화면이며, 그것을 *알려주는* 것이 알림의 본질이다.
2. **명시된 핵심 신호의 미통지**: spring-app 대시보드의 "Pending & timeouts" 패널 설명에 운영자가 직접 *"pending > 0 또는 timeouts/s 발생 = 풀 고갈 신호 (이 변경의 핵심 관측 대상)"*이라 적어두었으나, 이를 능동적으로 통지할 수단이 없다.

본 변경은 Grafana Cloud Alerting으로 **대시보드 패널을 미러링하는 알림**을 구성하고 Discord로 라우팅한다. 각 알림은 actionable 패널에 1:1로 대응해 같은 쿼리를 쓴다("보는 것 = 울리는 것" — 알림이 울리면 볼 패널이 자명하다). 단 앱 다운은 패널이 없는 사각지대이므로 `Up / replicas` 패널을 신설해 미러링으로 덮는다. 알림 규칙은 UI에서 정의하되(클릭옵스), 대시보드와 동일하게 `infra/grafana/`에 export 스냅샷으로 두어 "무엇을·왜 알리는지"의 이력을 git에 남긴다.

## What Changes

### 운영 (Grafana Cloud UI — 클릭옵스)

- **Contact point 2개**: `#nomat-critical`(Discord webhook, @here 멘션), `#nomat-warning`(멘션 없음)
- **Notification policy**: `severity` 라벨 기반 라우팅 (`critical` → critical 채널, `warning` → warning 채널). `group by [alertname, instance]`로 replica별 분리
- **`Up / replicas` 패널 신설**: spring-app 대시보드에 `sum(up{job="spring-app"})` 패널 추가 (앱다운 알림이 미러링할 패널 — 사각지대 보정)
- **Alert rule** (Grafana-managed, 폴더 `nomat`, 평가 주기 1m) — 세 대시보드의 actionable 패널을 미러링:
  - 🔴 critical 6: 앱 완전 다운, 관측 두절(`absent(up)`), Hikari timeout, 5xx 비율(트래픽 가드), data 노드 디스크, 파일시스템 읽기전용
  - 🟡 warning 9: Hikari pending, heap 압박(클러스터), ERROR 로그 급증, GC 과부하, CPU 높음, 최대 응답시간, Tomcat 포화, 노드 메모리/CPU, app 노드 디스크
  - 순수 관측 패널(Request rate, Threads, Classes loaded 등)은 미러링 제외

### infra/

- `infra/grafana/dashboards/*.json`: 현재 **untracked** 상태인 대시보드 3종(spring-app, node-exporter, nomat-log) export 스냅샷을 git에 편입
- `infra/grafana/alerting/`: UI에서 정의한 alert rule group을 export(YAML/JSON)하여 스냅샷으로 보관
- `infra/grafana/README.md` 신규: 관측 자산 컨벤션(원천=Grafana Cloud UI, git=스냅샷, 재-export 방법, 드리프트 주의) + 알림 카탈로그(의도의 git 이력)
- `infra/CLAUDE.md`: `## 구조` 트리에 `grafana/` 항목 추가, 관측 자산 운영 방식 1줄 기술

### 운영 (코드 변경 외)

- Discord 서버에 `#nomat-critical`, `#nomat-warning` 채널 + 각 채널 webhook URL 발급
- (선택) 향후 percentile 응답시간 알림이 필요하면 `application-metrics`에서 히스토그램 활성화를 별도 변경으로 — 카디널리티 예산(10k active series) 트레이드오프 동반

## Capabilities

### New Capabilities
- `observability-alerting`: Grafana Cloud Alerting으로 대시보드 패널을 미러링하는 알림을 구성해 Discord로 통지하고, 패널 없는 앱다운은 Up 패널 신설로 덮으며, 롤링 배포·저트래픽·관측 두절을 오탐 없이 다루고, 알림 규칙·대시보드를 `infra/grafana/`에 버전 관리하는 능력

### Modified Capabilities
- (해당 없음) — `observability-pipeline`의 "운영 모니터링 UI는 Grafana Cloud로 단일화" 요구사항은 알림 UI의 *위치*만 정하고 구체 규칙·라우팅·정책은 정의하지 않는다. 알림은 독립 관심사이므로 신규 capability로 분리한다.

## Impact

- **서브프로젝트**: `infra/`만 해당 — `infra/grafana/`(신규 디렉토리: dashboards·alerting·README), `infra/CLAUDE.md`. `back/`·`front/` 코드 변경 **없음**
- **도메인 모듈**: 없음 (playlist/room/player/favoriteplaylist/auth 무관)
- **헥사고날 계층**: 없음 — 백엔드 코드(in/out/application) 무변경. 새 빈·어댑터·엔드포인트 추가 없음
- **DB 스키마 / ES 매핑 / Kafka 토픽 / Redis 키**: 모두 변경 없음
- **메트릭 카디널리티**: 알림은 기존 allow-list 메트릭(`jvm_*`·`hikaricp_*`·`http_server_requests*`·`logback_events*`·`tomcat_*`·`up`·node-exporter)만 쿼리 — **신규 series 생성 없음**. Mimir 10k 예산 무영향
- **외부 시스템**:
  - Grafana Cloud Alerting (기존 스택 내 신규 기능 사용) — Loki·Mimir를 쿼리하는 Grafana-managed 알림
  - Discord webhook (신규 통지 의존) — 알림 라우팅 대상. webhook URL은 contact point에 저장(저장소 미commit)
- **운영 동작**:
  - 증상 발생 시 Discord 채널로 통지. critical은 @here 멘션, warning은 조용히
  - 롤링 배포(start-first, parallelism 1) 중 per-replica series churn을 NoData=OK + 긴 `for`로 흡수 → 배포 오탐 없음
  - Grafana Cloud/Discord 외부 단절 시 알림 일시 중단(앱 자체는 정상). 관측 두절 자체는 `absent(up)` 룰이 감지
- **비용**: Grafana Cloud Free tier 알림 한도 내. Discord webhook 무료
- **롤백**: UI에서 알림 규칙·contact point 비활성화/삭제. git 아티팩트(`infra/grafana/`, `infra/CLAUDE.md`)는 단일 PR revert. 앱·파이프라인·대시보드 무영향
