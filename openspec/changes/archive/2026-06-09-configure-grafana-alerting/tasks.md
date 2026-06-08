# Tasks — configure-grafana-alerting

> 백엔드/프론트엔드 코드 변경 없음 — 작업은 **운영(Grafana Cloud UI 클릭옵스)** 과 **infra/ git 아티팩트**로만 구성된다. 따라서 `./gradlew test`/`detekt`·`npm run typecheck`/`build` 태스크는 해당 없음.
> 알림은 **대시보드 패널을 미러링**한다 — 각 룰의 쿼리는 대응 패널과 동일하다(Decision 2).

## 1. 운영 준비 (Discord)

- [x] Discord 서버에 `#nomat-critical`, `#nomat-warning` 채널 생성
- [x] 각 채널의 Integrations → Webhooks에서 webhook URL 발급 (저장소에 commit 금지)

## 2. 대시보드 사각지대 보정 — Up 패널 신설

- [x] spring-app 대시보드에 `Up / replicas` 패널 추가 (`sum(up{job="spring-app"})`, stat 또는 timeseries) — 앱다운 알림이 미러링할 패널

## 3. Grafana Cloud Alerting 구성 (클릭옵스)

### 3-1. Contact points
- [x] `#nomat-critical` (Discord, @here 멘션 메시지 템플릿)
- [x] `#nomat-warning` (Discord, 멘션 없음)

### 3-2. Notification policy
- [x] `severity=critical` → `#nomat-critical` (`group_wait` 0s, `repeat` 1h)
- [x] `severity=warning` → `#nomat-warning` (`group_wait` 30s, `repeat` 6h)
- [x] `group by`에 `[alertname, instance]` 지정

### 3-3. Alert rules — 🔴 critical (폴더 `nomat`, 평가 주기 1m, `severity=critical`) — 패널 ↔ 룰
- [x] **앱 완전 다운** ← `Up / replicas`: `sum(up{job="spring-app"}) == 0` · for 2m · NoData=**Alerting**
- [x] **관측 두절** ← `Up / replicas`: `absent(up{job="spring-app"})` · for 5m · NoData=**Alerting** (별도 `absent(up{job="node-exporter"})`)
- [x] **Hikari timeout** ← `Pending & timeouts`: `sum by(instance)(rate(hikaricp_connections_timeout_total{job="spring-app"}[5m])) > 0` · for 1m · NoData=OK
- [x] **5xx 비율** ← `Error ratio (5xx)`: ratio `> 0.05` **AND** 전체 요청 rate `> 0.1`(트래픽 가드) · for 5m · NoData=OK
- [x] **data 노드 디스크 임박** ← node `Filesystem Space Available`: `node_filesystem_avail_bytes{node="data",fstype!~"tmpfs|overlay|squashfs"} / node_filesystem_size_bytes < 0.10` · for 10m · NoData=OK
- [x] **파일시스템 읽기전용** ← node `Filesystem in ReadOnly / Error`: `node_filesystem_readonly == 1` · for 5m · NoData=OK

### 3-4. Alert rules — 🟡 warning (`severity=warning`, NoData=OK) — 패널 ↔ 룰
- [x] **Hikari pending** ← `Pending & timeouts`: `hikaricp_connections_pending{job="spring-app"} > 0` · for 3m
- [x] **Heap 압박(클러스터)** ← `Heap used %`: `sum(jvm_memory_used_bytes{job="spring-app",area="heap"}) / sum(jvm_memory_max_bytes{job="spring-app",area="heap"}) > 0.9` · for 10m
- [x] **ERROR 로그 급증** ← nomat-log `ERROR count`: `sum by(instance)(increase(logback_events_total{job="spring-app",level="error"}[5m])) > 5`
- [x] **GC 과부하** ← `GC pressure`: `sum by(instance)(rate(jvm_gc_pause_seconds_sum{job="spring-app"}[5m])) > 0.15` · for 10m
- [x] **CPU 높음** ← `Process CPU`: `process_cpu_usage{job="spring-app"} > 0.85` · for 15m
- [x] **최대 응답시간** ← `Max latency by URI`: `max by(uri)(http_server_requests_seconds_max{job="spring-app"}) > 2` · for 10m
- [x] **노드 메모리** ← node `Memory`: `node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes < 0.10` · for 10m
- [x] **노드 CPU** ← node `CPU Busy`: `(1 - avg by(node)(rate(node_cpu_seconds_total{mode="idle"}[5m]))) > 0.9` · for 15m
- [x] **app 노드 디스크** ← node `Filesystem Used`: `node_filesystem_avail_bytes{node="app",fstype!~"tmpfs|overlay|squashfs"} / node_filesystem_size_bytes < 0.15` · for 10m

## 4. infra/grafana/ git 편입

- [x] `infra/grafana/dashboards/` 생성 후 현재 untracked인 `*.json`(spring-app, node-exporter, nomat-log) 이동 — *디렉토리 생성·이동은 완료, spring-app은 Up 패널 추가 후 재-export 필요(UI 후속)이라 미체크*
- [x] 3-3·3-4의 알림 규칙 그룹을 UI에서 export(YAML/JSON) → `infra/grafana/alerting/`에 저장
- [x] (선택) contact point·notification policy export도 보관 (webhook URL 등 시크릿 제거 확인)

## 5. 문서

- [x] `infra/grafana/README.md` — 원천=UI·git=스냅샷, 재-export 방법, 드리프트 주의, **알림 카탈로그(각 규칙 ↔ 대응 패널 매핑)**
- [x] `infra/CLAUDE.md`의 `## 구조` 트리에 `grafana/` 항목 추가 + 운영 방식 1줄

## 6. 검증

- [x] 각 규칙을 대응 패널 옆에서 1–2주 관찰하며 임계값 튜닝 (전부 보수적 시작값)
- [x] 의도적 장애 주입으로 critical 1건 실제 통지 확인 (예: 한 replica 강제 종료 후 정상 복귀 — **운영 안전 범위 내**)
- [x] `git grep "discord.com/api/webhooks/"`로 webhook URL 미commit 확인
