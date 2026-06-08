## Why

현재 운영의 observability 스택은 ELK(자체 운영 Logstash + Kibana + Elasticsearch 로그 인덱스)와 self-hosted Prometheus·Grafana로 분리 운영되고 있다. 이 구성은 다음과 같은 운영 부담을 가진다:

1. **운영 컴포넌트 수 과다**: Logstash·Kibana·Prometheus·Grafana 4개 자체 운영. 각자의 업그레이드·헬스체크·디스크 관리·인증 설정 필요
2. **ES 인덱스의 이중 책임**: Elasticsearch 인스턴스가 **플레이리스트 검색용**(Nori 분석기, `EsPlaylistSyncHandler` 동기화)과 **로그 저장용**(logstash-*)으로 동시에 사용되어 자원 경합·인덱스 정책 복잡화
4. **커스텀 이미지 운영**: Prometheus는 `entrypoint.sh`에서 `${DATA_NODE_IP}`를 sed로 치환하는 [커스텀 이미지](../../infra/prometheus/)로 빌드되어 추가 CI·이미지 레지스트리 항목 발생
5. **모니터링 분기**: 로그는 Kibana, 메트릭/대시보드는 Grafana로 분기되어 인시던트 시 컨텍스트 전환 비용

Grafana Cloud의 Free tier(50GB logs·10k active series·14일 보관)는 본 서비스 규모에 충분하며, **Grafana Alloy**(Promtail의 후속, 2026-03-02 Promtail EOL) 단일 에이전트로 로그·메트릭을 통합 수집·push할 수 있다. 본 변경은 ELK 로그 경로와 self-hosted 모니터링 컴포넌트를 Grafana Cloud + Alloy로 일괄 교체한다. **Elasticsearch는 플레이리스트 검색 책임만 남기고 그대로 유지**한다 (로그 인덱스만 제거).

## What Changes

### back/
- **BREAKING**: `back/build.gradle.kts`에서 `ch.qos.logback.access:logback-access-common`, `ch.qos.logback.access:logback-access-tomcat` 의존성 제거. `net.logstash.logback:logstash-logback-encoder`는 **유지** (TCP socket appender 대신 ConsoleAppender + JSON encoder로 재사용)
- `back/src/main/resources/logback-spring.xml`: dev 프로파일의 `LogstashTcpSocketAppender` → `ConsoleAppender` + `LogstashEncoder` (stdout JSON 출력)
- `back/src/main/resources/logback-access-dev.xml`: `LogstashAccessTcpSocketAppender` → `ConsoleAppender` + `LogstashAccessEncoder` (stdout JSON 출력)
- `back/src/main/resources/logback-access-local.xml`, `logback-access-test.xml`: 변경 없음 (이미 ConsoleAppender 사용)
- `back/src/main/resources/application.yml`의 dev 프로파일에서 `LOGSTASH_URL` 의존 제거
- `back/CLAUDE.md`: 기술 스택에서 ELK 언급 정리, observability 섹션 신설(또는 기존 운영 엔드포인트 섹션에 부기)

### infra/
- **BREAKING**: `infra/data/compose.yml`에서 `logstash` 서비스 + 관련 파일(`logstash-pipeline.conf`, `logstash-config.yml`, `elk` 네트워크) 제거
- **BREAKING**: `infra/app/compose.yml`에서 `kibana`(+ `kibana.yml`), `grafana`(+ `grafana-data` 볼륨), `prometheus`(+ `prometheus-data` 볼륨), `node-exporter` 서비스 제거. `local-prometheus` 네트워크 제거
- **BREAKING**: `infra/prometheus/` 디렉토리 전체 제거 (커스텀 Prometheus 이미지 + `entrypoint.sh` 더 이상 불필요)
- `infra/app/prometheus.yml` 제거
- `infra/app/compose.yml`에 `alloy` 서비스 추가 (`mode: global` — Swarm 노드당 1개). Docker socket 마운트 + `loki.source.docker`로 컨테이너 stdout 자동 수집. node-exporter scrape 후 Mimir에 push
- `infra/app/alloy-config.alloy` 신규: Alloy 설정(loki write endpoint, mimir remote_write, node-exporter scrape 정의)
- `infra/data/compose.yml`에 `alloy` 서비스 추가 (단일 compose 컨테이너). data 노드의 컨테이너 로그 + node-exporter scrape 후 Grafana Cloud로 push
- `infra/data/alloy-config.alloy` 신규
- `infra/data/compose.yml`의 `node-exporter` 서비스는 그대로 유지(Alloy가 scrape)
- `infra/CLAUDE.md`: ELK·Kibana·자체 Grafana/Prometheus 항목 정리, Alloy + Grafana Cloud 흐름 명시. Nginx config 업데이트 트릭(`nginx_conf-N`)과 동일하게 Alloy config도 Swarm config로 둘 경우 동일 룰 명시

### .github/workflows/
- `infra-push-develop.yml`, `back-push-develop.yml`: 시크릿 교체 — `LOGSTASH_URL` 제거. `GRAFANA_CLOUD_LOKI_URL`, `GRAFANA_CLOUD_LOKI_USER`, `GRAFANA_CLOUD_MIMIR_URL`, `GRAFANA_CLOUD_MIMIR_USER`, `GRAFANA_CLOUD_API_TOKEN` 추가

### 운영 (코드 변경 외)
- Grafana Cloud 스택 생성, write tokens 발급 (Loki + Mimir 각 user ID, 공용 API token)
- self-hosted Grafana 대시보드는 모두 임시(volume 기반·provisioning 없음)이므로 **새로 작성**(Cloud Grafana에서). 기존 dashboard JSON export는 운영 선택
- EC2 `.env`에서 `LOGSTASH_URL`, `ELASTICSEARCH_PASSWORD`의 Kibana용 사용 정리. Grafana Cloud 시크릿 추가
- ES에 남은 logstash-* 인덱스 일괄 삭제 (디스크 회수)
- self-hosted Grafana 컨테이너의 alerts·users·datasources는 Cloud로 이전 (없거나 최소)

## Capabilities

### New Capabilities
- `observability-pipeline`: Grafana Alloy 단일 에이전트로 백엔드/인프라의 로그·메트릭을 Grafana Cloud(Loki + Mimir)로 수집·전송하고, Grafana Cloud Grafana UI에서 대시보드·알림을 통합 운영하는 능력

### Modified Capabilities
- (해당 없음 — 기존 capabilities는 logging/monitoring 책임을 포함하지 않음)

## Impact

- **서브프로젝트**: `back/`(logback·logback-access·application.yml·build.gradle.kts·CLAUDE.md), `infra/`(app·data·prometheus 디렉토리 정리, alloy 설정 추가, CLAUDE.md), `.github/workflows/`(시크릿 교체). `front/` 영향 없음
- **도메인 모듈**: 직접 변경 없음. `playlist`/`favoriteplaylist`의 ES sync 동작은 별도 인덱스를 사용하므로 무관
- **헥사고날 계층**: 신규 빈/어댑터 추가 없음. logback XML 설정과 Spring Boot 표준 설정만 변경. `application/`, `in/`, `out/` 변경 없음
- **DB 스키마**: 변경 없음
- **ES 매핑**: 변경 없음. `PlaylistDocument` 매핑 그대로 유지. 단, 운영 작업으로 logstash-* 인덱스 일괄 삭제(검색 인덱스 무관)
- **Kafka 토픽**: 해당 없음 (이전 변경에서 Kafka 인프라 제거됨)
- **Redis 키**: 변경 없음
- **의존성**: 제거 — `logback-access-common`, `logback-access-tomcat`. 유지 — `logstash-logback-encoder` (encoder 클래스만 재사용)
- **외부 시스템**: Grafana Cloud 신규 의존 추가 (Loki·Mimir·Grafana). 인증은 HTTPS Basic Auth(user ID + API token). 무료 tier 한도 내 사용 가정
- **인프라 컨테이너 변화**: app 노드 `kibana`/`grafana`/`prometheus`/`node-exporter` 제거 + `alloy` 추가. data 노드 `logstash` 제거 + `alloy` 추가. 커스텀 prometheus 이미지 빌드 제거. **net 컨테이너 -4 +2 = -2**
- **운영 동작**:
  - 로그는 spring-app stdout으로 출력 → Alloy가 Docker socket 통해 수집 → Loki push (앱 컨테이너 재시작 시 로그 손실 없음 — Docker가 stdout buffer 유지)
  - 메트릭은 Alloy가 node-exporter 직접 scrape 후 Mimir push (Prometheus 중간 단계 제거)
  - Kibana URL은 더 이상 접근 불가 — Grafana Cloud Grafana UI로 통합. self-hosted Grafana URL도 동일하게 종료
  - Alloy 자체가 다운되면 그 시간의 로그/메트릭 손실 (Docker 로그 buffer가 짧은 정전은 흡수)
  - Grafana Cloud 외부 의존 → 인터넷 단절 시 dev 환경 모니터링 일시 중단 (단, 앱 자체는 정상 동작)
- **롤백**: 단일 PR `git revert` + `infra/data` 수동 재배포(Logstash 재가동) + EC2 `.env` 원복. ELK 데이터는 새로 시작(이전 로그 인덱스는 보존되지 않음). 자세한 절차는 design.md Decision 11
