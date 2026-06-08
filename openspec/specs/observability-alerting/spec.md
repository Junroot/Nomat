# observability-alerting Specification

## Purpose

Grafana Cloud 대시보드 패널을 원천으로 한 알림(alerting) 역량을 정의한다. "보는 것 = 울리는 것" 원칙에 따라 각 알림 규칙은 actionable 대시보드 패널에 1:1로 대응하며, 동일 쿼리·식·라벨과 패널의 red zone 임계를 사용한다. 앱 가용성·HikariCP 풀 고갈·오류 신호·자원 압박을 통지하되, `env=dev` 단일 저트래픽 환경 특성을 고려해 트래픽 바닥 가드와 절대 건수 임계로 노이즈를 억제한다. 알림은 `severity` 라벨(`critical`|`warning`)로 Discord 2채널에 분리 라우팅하며, webhook 자격증명은 저장소에 commit하지 않는다. 대시보드·알림 규칙 자산은 `infra/grafana/`에 export 스냅샷으로 버전 관리한다(Grafana Cloud UI가 원천, git은 스냅샷).

## Requirements

### Requirement: 알림은 대시보드 패널을 미러링한다

시스템의 각 알림 규칙은 대시보드의 **actionable 패널**에 1:1로 대응해야(MUST) 한다. 알림 쿼리는 대응 패널의 쿼리와 동일한 식·라벨을 사용하고, 임계는 패널의 red zone(또는 패널에 임계가 없으면 운영 합의값)을 사용한다. 알림이 발화하면 운영자가 볼 패널이 자명해야 한다("보는 것 = 울리는 것"). 순수 관측용 패널(임계 없이 추세만 보는 패널)은 알림을 만들지 않는다.

#### Scenario: actionable 패널은 대응 알림을 가진다
- **WHEN** spring-app 대시보드의 "Pending & timeouts", "Heap used %", "GC pressure", "Process CPU", "Tomcat threads", "Error ratio (5xx)", "Max latency by URI" 패널을 검사
- **THEN** 각 패널과 동일 쿼리를 사용하는 알림 규칙이 존재해야 한다

#### Scenario: 순수 관측 패널은 알림이 없다
- **WHEN** "Request rate", "Threads", "Classes loaded", "GC collection rate", "Connections", "Tomcat sessions" 등 임계가 없는 관측 패널을 검사
- **THEN** 대응 알림 규칙이 존재하지 않아야 한다 (알림 피로 방지)

#### Scenario: node-exporter는 핵심 패널만 미러링
- **WHEN** node-exporter(커뮤니티 143패널) 대시보드 기준 알림을 검사
- **THEN** 디스크(Filesystem Available, Filesystem ReadOnly/Error)·메모리(Memory available)·CPU(CPU Busy) 핵심 패널만 미러링되고 나머지 패널은 알림이 없어야 한다

### Requirement: 앱 가용성은 신설한 Up 패널을 미러링해 통지

시스템은 spring-app 대시보드에 **`Up / replicas` 패널(`sum(up{job="spring-app"})`)을 신설**하고 이를 미러링해 앱 완전 다운·관측 두절을 critical로 통지해야(MUST) 한다. 앱 다운은 기존 어느 패널로도 드러나지 않는 사각지대이므로(죽은 앱의 대시보드는 빈 화면), 미러링 원칙을 유지하기 위해 패널을 먼저 만든다.

#### Scenario: Up / replicas 패널 존재
- **WHEN** spring-app 대시보드를 검사
- **THEN** `sum(up{job="spring-app"})`를 표시하는 패널이 존재해야 한다

#### Scenario: 두 replica가 모두 응답 불가
- **WHEN** `sum(up{job="spring-app"})`가 0인 상태가 2분 이상 지속
- **THEN** `#nomat-critical` Discord 채널에 @here 멘션과 함께 통지가 전송되어야 한다

#### Scenario: 메트릭 수집 두절 (Alloy/스크레이프 단절)
- **WHEN** `absent(up{job="spring-app"})`가 5분 이상 참
- **THEN** `#nomat-critical`에 관측 두절 통지가 전송되어야 한다
- **AND** 이 규칙의 NoData 동작은 `Alerting`이어야 한다 (데이터 부재가 곧 위험 신호이므로)

#### Scenario: 정상 롤링 배포는 오탐을 내지 않음
- **WHEN** Docker Swarm이 `order=start-first, parallelism=1`로 한 번에 1 replica만 교체
- **THEN** 최소 1 replica가 항상 살아 있어 `sum(up{job="spring-app"})`가 0이 되지 않으므로 앱 다운 알림이 발생하지 않아야 한다

### Requirement: HikariCP 풀 고갈은 "Pending & timeouts" 패널을 미러링

시스템은 spring-app 대시보드 "Pending & timeouts" 패널을 미러링해 HikariCP 커넥션 풀 고갈을 통지해야(MUST) 한다. 이는 운영자가 패널 설명에 "핵심 관측 대상"으로 명시한 신호다. 외부 시스템: MySQL 커넥션 풀.

#### Scenario: 커넥션 획득 timeout 발생
- **WHEN** `sum by(instance)(rate(hikaricp_connections_timeout_total{job="spring-app"}[5m]))`가 0보다 큰 상태가 1분 이상 지속
- **THEN** `#nomat-critical`에 통지가 전송되어야 한다

#### Scenario: 커넥션 대기 스레드 누적
- **WHEN** `hikaricp_connections_pending{job="spring-app"}`가 0보다 큰 상태가 3분 이상 지속
- **THEN** `#nomat-warning`에 통지가 전송되어야 한다

### Requirement: 오류 신호 알림은 패널을 미러링하되 저트래픽에 견고하게

시스템은 spring-app "Error ratio (5xx)" 패널과 nomat-log "ERROR count" 패널을 미러링해 오류를 통지해야(MUST) 한다. `env=dev` 단일 저트래픽 환경이므로 비율 알림에는 트래픽 바닥 가드를, 로그 알림에는 절대 건수 임계를 적용해 소수 요청 노이즈를 방지해야 한다. 외부 시스템: Mimir(메트릭).

#### Scenario: 5xx 비율 — 트래픽 가드 결합
- **WHEN** `sum(rate(http_server_requests_seconds_count{job="spring-app",status=~"5.."}[5m])) / clamp_min(sum(rate(http_server_requests_seconds_count{job="spring-app"}[5m])),0.0001)`가 0.05를 초과
- **AND** 전체 요청 rate가 0.1을 초과(트래픽 바닥 가드)한 상태가 5분 이상 지속
- **THEN** `#nomat-critical`에 통지가 전송되어야 한다

#### Scenario: ERROR 로그 급증
- **WHEN** `sum by(instance)(increase(logback_events_total{job="spring-app",level="error"}[5m]))`가 임계(시작값 5)를 초과
- **THEN** `#nomat-warning`에 통지가 전송되어야 한다
- **AND** 메트릭 라벨은 소문자 `level="error"`를 사용한다 (Loki 로그 라벨의 대문자 `ERROR`와 구분)

### Requirement: 자원 압박 알림은 해당 패널을 미러링

시스템은 자원 패널을 미러링해 고갈 추세를 통지해야(MUST) 한다. Heap은 패널과 동일하게 클러스터 단위로 평가하고, GC·CPU·Tomcat·최대 응답시간·노드 자원은 각 패널 쿼리를 그대로 쓴다. 외부 시스템: Mimir(메트릭, node-exporter 포함).

#### Scenario: Heap 압박 (패널과 동일한 클러스터 단위)
- **WHEN** `sum(jvm_memory_used_bytes{job="spring-app",area="heap"}) / sum(jvm_memory_max_bytes{job="spring-app",area="heap"})`가 0.9를 초과한 상태가 10분 이상 지속
- **THEN** `#nomat-warning`에 통지가 전송되어야 한다
- **AND** 평가는 "Heap used %" 패널과 동일하게 instance 합산(클러스터 단위)이어야 한다

#### Scenario: GC·CPU·Tomcat·최대 응답시간 포화
- **WHEN** GC pause 비율(`rate(jvm_gc_pause_seconds_sum[5m]) > 0.15`), Process CPU(`process_cpu_usage > 0.85`), Tomcat 스레드(`tomcat_threads_busy_threads / tomcat_threads_config_max_threads > 0.85`), 또는 Max latency(`max by(uri)(http_server_requests_seconds_max) > 2`) 중 하나가 지정 `for` 기간 지속
- **THEN** `#nomat-warning`에 통지가 전송되어야 한다

#### Scenario: 노드 디스크·파일시스템·메모리·CPU
- **WHEN** data 노드 디스크(`node_filesystem_avail_bytes / node_filesystem_size_bytes < 0.10`)·파일시스템 읽기전용(`node_filesystem_readonly == 1`)은 critical, 노드 메모리(`MemAvailable/MemTotal < 0.10`)·CPU Busy(`> 0.9`)·app 노드 디스크(`< 0.15`)는 warning 조건이 지정 `for` 기간 지속
- **THEN** 해당 심각도 채널에 통지가 전송되어야 한다

### Requirement: 알림 라우팅은 severity 라벨로 Discord 2채널 분리

시스템은 각 알림 규칙의 `severity` 라벨(`critical`|`warning`)에 따라 통지를 분리 라우팅해야(MUST) 한다. 외부 시스템: Discord webhook.

#### Scenario: critical은 멘션과 함께 critical 채널로
- **WHEN** `severity=critical` 라벨이 붙은 알림이 발화
- **THEN** `#nomat-critical` contact point(Discord)로 라우팅되며 @here 멘션을 포함해야 한다

#### Scenario: warning은 멘션 없이 warning 채널로
- **WHEN** `severity=warning` 라벨이 붙은 알림이 발화
- **THEN** `#nomat-warning` contact point로 라우팅되며 멘션 없이 전송되어야 한다

#### Scenario: webhook 자격증명은 저장소에 commit되지 않음
- **WHEN** 저장소 전체에서 Discord webhook URL 패턴(`discord.com/api/webhooks/`)을 검색
- **THEN** 실제 webhook URL이 commit된 파일에 존재하지 않아야 한다 (URL은 Grafana contact point 설정에만 보관)

### Requirement: 관측 자산(대시보드·알림 규칙)은 infra/grafana/에 export 스냅샷으로 버전 관리

시스템은 Grafana Cloud UI를 원천으로 하되, 대시보드와 알림 규칙의 export 스냅샷을 `infra/grafana/`에 두어 버전 이력·리뷰·재해복구를 가능하게 해야(MUST) 한다. CI 자동 반영은 하지 않으며(스냅샷 모델), 재-export는 수동임을 문서로 명시해야 한다. Up 패널 신설 등 대시보드 변경분도 재-export해 반영한다.

#### Scenario: 대시보드 export가 git에 존재 (Up 패널 포함)
- **WHEN** `infra/grafana/dashboards/` 디렉토리를 검사
- **THEN** spring-app · node-exporter · 로그 대시보드의 JSON export가 git에 추적되어야 하고, spring-app export에는 신설한 `Up / replicas` 패널이 포함되어야 한다

#### Scenario: 알림 규칙 export가 git에 존재
- **WHEN** `infra/grafana/alerting/` 디렉토리를 검사
- **THEN** UI에서 정의한 알림 규칙 그룹의 export(YAML/JSON)가 존재해야 한다

#### Scenario: 컨벤션 문서가 원천·재-export·드리프트·미러링을 명시
- **WHEN** `infra/grafana/README.md`를 검사
- **THEN** "Grafana Cloud UI가 원천, git은 스냅샷", 재-export 방법, 드리프트 주의, 그리고 알림 카탈로그(각 규칙 ↔ 대응 패널 매핑)가 기술되어 있어야 한다
