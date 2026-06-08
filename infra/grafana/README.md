# Grafana 관측 자산 (대시보드 · 알림)

Grafana Cloud에서 운영하는 관측 자산의 **export 스냅샷**이다.

## 진실의 원천 (source of truth)

**Grafana Cloud UI가 원천이고 이 파일들은 스냅샷이다.** 버전 이력·리뷰·재해복구(재import)를 위해 git에 둔다. CI가 자동 반영하지 않으므로, UI에서 바꾸면 **수동으로 다시 export해 커밋**해야 git이 따라온다(드리프트 주의). 완전 IaC가 필요해지면 Terraform grafana provider로 전환한다.

## 구성

```
dashboards/   — 대시보드 export (apiVersion: dashboard.grafana.app/v2)
  spring-app.json      직접 큐레이션 — JVM / HTTP(Spring MVC) / HikariCP / Tomcat / GC
  node-exporter.json   커뮤니티 Node Exporter Full (노드 CPU/메모리/디스크/네트워크)
  nomat-log.json       Loki 로그 조회 (app/access 로그, level별 집계, ERROR 추적)
alerting/     — 알림 규칙 그룹 export (UI에서 만든 뒤 export)
```

## 재-export 방법

- **대시보드**: 대시보드 → Export → "Export as JSON" / Save to file
- **알림 규칙**: Alerting → Alert rules → 그룹 More → Export (Provisioning 포맷: YAML/JSON/Terraform 중 택1)
- **Contact point / Notification policy**: Alerting → 각 화면 Export

## 알림 설계 원칙

알림은 **대시보드 패널을 미러링**한다 — 각 알림은 actionable 패널에 1:1 대응하고 같은 쿼리를 쓴다("보는 것 = 울리는 것"). 순수 관측 패널(Request rate, Threads, Classes loaded 등)은 알림을 만들지 않는다. 앱 다운은 패널이 없는 사각지대이므로 spring-app에 `Up / replicas` 패널(`sum(up{job="spring-app"})`)을 신설해 미러링으로 덮는다.

**NoData 원칙**: `up`/`absent` 계열만 **Alerting**(관측이 머는 게 위험), 나머지 per-replica·노드 룰은 **OK** — 롤링 배포(start-first)로 컨테이너가 잠깐 사라지는 series churn을 오탐으로 보지 않기 위함.

## 알림 카탈로그 (규칙 ↔ 대응 패널)

라우팅: `severity` 라벨 → `#nomat-critical`(@here) / `#nomat-warning`. `group by [alertname, instance]`.

### 🔴 critical — #nomat-critical (@here)
| 알림 | 대응 패널 | 조건 | for | NoData |
|------|-----------|------|-----|--------|
| 앱 완전 다운 | Up / replicas | `sum(up{job="spring-app"}) == 0` | 2m | **Alerting** |
| 관측 두절 | Up / replicas | `absent(up{job="spring-app"})` (+ node-exporter) | 5m | **Alerting** |
| Hikari timeout | Pending & timeouts | `sum by(instance)(rate(hikaricp_connections_timeout_total[5m])) > 0` | 1m | OK |
| 5xx 비율 | Error ratio (5xx) | ratio `> 0.05` AND 전체 rate `> 0.1`(트래픽 가드) | 5m | OK |
| data 노드 디스크 | Filesystem Available | `avail/size{node="data"} < 0.10` | 10m | OK |
| 파일시스템 읽기전용 | Filesystem ReadOnly/Error | `node_filesystem_readonly == 1` | 5m | OK |

### 🟡 warning — #nomat-warning
| 알림 | 대응 패널 | 조건 | for |
|------|-----------|------|-----|
| Hikari pending | Pending & timeouts | `hikaricp_connections_pending > 0` | 3m |
| Heap 압박(클러스터) | Heap used % | `sum(used{area="heap"})/sum(max{area="heap"}) > 0.9` | 10m |
| ERROR 로그 급증 | ERROR count (nomat-log) | `increase(logback_events_total{level="error"}[5m]) > 5` | 0m |
| GC 과부하 | GC pressure | `rate(jvm_gc_pause_seconds_sum[5m]) > 0.15` | 10m |
| CPU 높음 | Process CPU | `process_cpu_usage > 0.85` | 15m |
| 최대 응답시간 | Max latency by URI | `max by(uri)(http_server_requests_seconds_max) > 2` | 10m |
| Tomcat 포화 | Tomcat threads | `busy/config_max > 0.85` | 5m |
| 노드 메모리 | Memory | `MemAvailable/MemTotal < 0.10` | 10m |
| 노드 CPU | CPU Busy | `(1 - rate(node_cpu_seconds_total{mode="idle"}[5m])) > 0.9` | 15m |
| app 노드 디스크 | Filesystem Used | `avail/size{node="app"} < 0.15` | 10m |

> **라벨 함정**: 메트릭은 `logback_events_total{level="error"}`(소문자), Loki는 `{level="ERROR"}`(대문자).
> **임계값**: 전부 보수적 시작값 — 대응 패널을 1~2주 관찰하며 튜닝한다.
> **응답시간 percentile**: `application-metrics`에서 히스토그램 OFF(카디널리티 가드)라 p95/p99 불가. 평균·max만 미러링.
