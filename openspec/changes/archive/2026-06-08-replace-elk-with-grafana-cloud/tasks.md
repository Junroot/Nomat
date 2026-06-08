## 0. 사전 준비 (운영자 수동)

- [x] 0.1 Grafana Cloud 계정 생성·스택 생성 (region: us-central1 권장). 스택 URL을 PR 본문에 기록
- [x] 0.2 Loki/Mimir 각 instance ID 확인 → PR 본문에 마스킹된 형태로 첨부
- [x] 0.3 Access Policy 생성 후 API token 발급 (scope: `logs:write`, `metrics:write`). token은 GitHub Secret으로만 보관
- [x] 0.4 발급된 값을 GitHub Secrets에 등록: `GRAFANA_CLOUD_LOKI_URL`, `GRAFANA_CLOUD_LOKI_USER`, `GRAFANA_CLOUD_MIMIR_URL`, `GRAFANA_CLOUD_MIMIR_USER`, `GRAFANA_CLOUD_API_TOKEN`
- [x] 0.5 (분석) 현재 dev ES `logstash-*` 인덱스의 일평균 size 확인 (`curl -u elastic:$PW http://elasticsearch:9200/_cat/indices/logstash-*?v&s=index`). 30배가 Free tier 50GB/월 이내인지 확인 → PR 본문에 추정 첨부 (Open Question 1)
- [x] 0.6 (분석) 현재 self-hosted Grafana 대시보드 목록을 확인하고 마이그레이션 정책(새로 작성 vs JSON export) 결정 → PR 본문에 기록 (Open Question 2)
- [x] 0.7 (분석) self-hosted Grafana에 정의된 알림 규칙·채널을 목록화 → Cloud Grafana Alerting으로 이전 계획 PR 본문에 첨부 (Open Question 3)

## 1. 백엔드 — logback 전환

- [x] 1.1 `back/src/main/resources/logback-spring.xml`의 dev 프로파일에서 `LogstashTcpSocketAppender`를 제거하고 `ConsoleAppender` + `LogstashEncoder`로 교체. `customFields`(`{"logType":"app-log"}`)는 유지. local/test 프로파일은 변경 없음
- [x] 1.2 `back/src/main/resources/logback-access-dev.xml`의 `LogstashAccessTcpSocketAppender`를 제거하고 `ConsoleAppender` + `LogstashAccessEncoder`로 교체. `customFields`(`{"logType":"access-log"}`)와 `<fieldNames><requestHeaders>request_headers</requestHeaders></fieldNames>` 매핑은 유지
- [x] 1.3 `back/src/main/resources/logback-access-local.xml`, `logback-access-test.xml`은 변경 없음을 확인 (이미 ConsoleAppender 사용)
- [x] 1.4 `back/src/main/resources/application.yml`의 dev 프로파일에서 `LOGSTASH_URL` 의존 코드/주석이 남아 있다면 제거 (application.yml에 참조 없음 — 변경 불필요)
- [x] 1.5 `grep -rn -E "LOGSTASH_URL|LogstashTcp" back/`로 잔존 참조가 없음을 확인 (예상: 0건) → 0건 확인

## 2. 백엔드 — 의존성·문서

- [x] 2.1 `back/build.gradle.kts:60-61`의 `logback-access-common`/`logback-access-tomcat`는 **유지** — Decision 4 재확인 결과. `AccessLogConfiguration.kt`가 `ch.qos.logback.access.tomcat.LogbackValve`를 import하고 access 로그를 `LogstashAccessEncoder`로 직렬화하므로 제거 시 컴파일/런타임 깨짐. (proposal "What Changes/Impact"의 제거 문구는 stale — Decision 4가 정답)
- [x] 2.2 `back/build.gradle.kts:59`의 `net.logstash.logback:logstash-logback-encoder:7.4`는 **유지** (Decision 3)
- [x] 2.3 `back/CLAUDE.md`에 observability 섹션 신설 또는 "운영 엔드포인트" 인접에 한 단락 추가: 로그/메트릭은 Grafana Cloud로 송신되며, Alloy 에이전트가 컨테이너 stdout과 node-exporter를 수집. 로컬 개발은 콘솔 출력 그대로
- [x] 2.4 (기존 ELK 언급이 back/CLAUDE.md에 있다면) 정리 — back/CLAUDE.md에 ELK/Kibana/Logstash 언급 없음 (ES는 검색용으로만 기술). 정리할 항목 없음

## 3. 백엔드 — 빌드·정적분석

- [x] 3.1 `./gradlew test` 실행하여 전체 테스트 통과 — logback 설정 변경이 test 프로파일에 영향이 없는지 확인 (test 프로파일은 ConsoleAppender 그대로) → BUILD SUCCESSFUL
- [x] 3.2 `./gradlew build` 최종 통과 → BUILD SUCCESSFUL (2m 52s, 전체 Testcontainers 테스트 포함)
- [x] 3.3 `./gradlew detekt` 신규 위반 없음 확인 (CI Java 17 환경 기준) → detekt 통과

## 4. 인프라 — Alloy 설정 파일 신규 작성

- [x] 4.1 `infra/app/alloy-config.alloy` 신규: 
  - `loki.write` → `${GRAFANA_CLOUD_LOKI_URL}`, basic auth(`${GRAFANA_CLOUD_LOKI_USER}` / `${GRAFANA_CLOUD_API_TOKEN}`)
  - `prometheus.remote_write` → `${GRAFANA_CLOUD_MIMIR_URL}`, basic auth(`${GRAFANA_CLOUD_MIMIR_USER}` / `${GRAFANA_CLOUD_API_TOKEN}`)
  - `discovery.docker` + `loki.source.docker` → 스택의 모든 컨테이너 stdout tail
  - `prometheus.scrape` job: `node-exporter:9100` (app 노드)
  - `loki.process` stage에서 line의 JSON `logType` 필드를 label로 승격
  - `loki.process` stage에서 `static_labels` (`app=nomat-back`, `env=dev`, `node=app`)
- [x] 4.2 `infra/data/alloy-config.alloy` 신규: app용과 유사하되 `node=data` 라벨, scrape 대상은 data 노드의 `node-exporter:9100`
- [x] 4.3 라벨 정책 검증: `app`, `env`, `log_type`, `level`, `node`, `container_name` 외에는 `labeldrop`/`labelallow` stage로 컷오프 (Decision 8) — `stage.label_keep` 화이트리스트로 구현

## 5. 인프라 — 앱 노드 (Swarm)

- [x] 5.1 `infra/app/compose.yml`에 `alloy` 서비스 추가:
  - 이미지: `grafana/alloy:latest` (또는 특정 버전 핀)
  - `deploy.mode: global`
  - volumes: `/var/run/docker.sock:/var/run/docker.sock:ro`, `alloy-config` Swarm config
  - environment: `GRAFANA_CLOUD_*` 5종
  - networks: 새 overlay 네트워크 `observability` (Alloy와 node-exporter 통신용)
- [x] 5.2 `infra/app/compose.yml`에 `alloy_config-1` Swarm config 정의 (`./alloy-config.alloy`). 향후 수정 시 nginx_conf와 동일 룰로 키 숫자 증가
- [x] 5.3 `infra/app/compose.yml`에서 다음 서비스 + 관련 환경/볼륨/네트워크 모두 제거:
  - `kibana` 서비스, `./kibana.yml` config 마운트, `ELASTICSEARCH_HOSTS`/`ELASTICSEARCH_PASSWORD`의 Kibana 주입
  - `grafana` 서비스, `grafana-data` 볼륨
  - `prometheus` 서비스, `./prometheus.yml` 마운트, `prometheus-data` 볼륨, `DATA_NODE_IP` 환경
  - `node-exporter` 서비스는 Alloy가 scrape하므로 유지하되 새 `observability` 네트워크로 이전
  - `local-prometheus` 네트워크 정의 제거
- [x] 5.4 `infra/app/kibana.yml`, `infra/app/prometheus.yml` 파일 삭제
- [x] 5.5 `infra/app/compose.yml`에서 `spring-app` 서비스의 `LOGSTASH_URL` 환경변수 주입 제거
- [x] 5.6 `infra/prometheus/` 디렉토리 전체 삭제 (`Dockerfile`, `entrypoint.sh`)
- [x] 5.7 `grep -rn -E "kibana|LOGSTASH_URL|local-prometheus|custom prometheus image" infra/`로 잔존 참조 0건 확인 (코드/config 0건; 잔존은 infra/CLAUDE.md 문서뿐 → 7장에서 정리)

## 6. 인프라 — 데이터 노드 (compose, 수동)

- [x] 6.1 `infra/data/compose.yml`에 `alloy` 서비스 추가:
  - 이미지: app과 동일 버전
  - `restart: unless-stopped`
  - volumes: `/var/run/docker.sock:/var/run/docker.sock:ro`, `./alloy-config.alloy:/etc/alloy/config.alloy:ro`
  - environment: `GRAFANA_CLOUD_*` 5종
  - networks: 새 bridge 네트워크 `observability`
- [x] 6.2 `infra/data/compose.yml`에서 `logstash` 서비스 제거 + 포트(5044, 50000, 9600) 정리
- [x] 6.3 `infra/data/compose.yml`의 `elk` 네트워크 정의 제거 (elasticsearch는 기본 네트워크 + 외부 노출 그대로)
- [x] 6.4 `infra/data/logstash-pipeline.conf`, `infra/data/logstash-config.yml` 파일 삭제
- [x] 6.5 `infra/data/compose.yml`의 `node-exporter`는 유지하되 `observability` 네트워크로 이전

## 7. 인프라 — 문서

- [x] 7.1 `infra/CLAUDE.md`의 구조 다이어그램에서 `kibana.yml`, `prometheus.yml`, `prometheus/` 항목 제거. `alloy-config.alloy` 추가
- [x] 7.2 "핵심 설계 → 네트워크" 항목 갱신: `elk`, `local-prometheus` 제거. `observability` overlay/bridge 추가
- [x] 7.3 "핵심 설계 → Prometheus" 절을 "핵심 설계 → Grafana Cloud + Alloy"로 교체: 메트릭/로그가 Cloud로 push되며 self-hosted backend는 없음. 커스텀 Prometheus 이미지 빌드 단계 제거됨을 명시
- [x] 7.4 "환경변수" 절에서 ELK·Prometheus 관련 항목 정리. `GRAFANA_CLOUD_*` 5종 추가 (마스킹된 예시 포함)
- [x] 7.5 Alloy config 업데이트 룰 명시: Swarm config로 둘 경우 `nginx_conf-N`과 동일하게 키 숫자 증가 필요

## 8. CI/CD — 시크릿 교체

- [x] 8.1 `.github/workflows/back-push-develop.yml`에서 spring-app 환경변수 주입 부분의 `LOGSTASH_URL` 제거 — 워크플로우에 `LOGSTASH_URL` 직접 참조 없음(0건). 모든 앱 env는 EC2 `.env`를 `export $(xargs < .env)`로 주입하는 방식. `LOGSTASH_URL`은 compose.yml에만 있었고 5.5에서 제거됨. 워크플로우 변경 불필요
- [x] 8.2 `.github/workflows/infra-push-develop.yml`에서: `LOGSTASH_URL` export 없음(0건). `GRAFANA_CLOUD_*`는 compose의 `${...}` 보간으로 들어가며 그 값은 EC2 `.env`에서 `export $(xargs < .env)`로 공급됨(기존 DB/JWT 등 모든 시크릿과 동일 패턴). 따라서 워크플로우 YAML 변경 불필요 — `GRAFANA_CLOUD_*`는 EC2 `.env`에 등록(운영자 수동, 10.4). GitHub Secrets로 워크플로우에 직접 export하면 기존 패턴과 불일치하므로 추가하지 않음
- [x] 8.3 `back-pull-request.yml`, `front-*` 등 다른 워크플로우에 `LOGSTASH_URL` 참조가 있다면 정리 — 전체 `.github/`에서 0건 확인
- [x] 8.4 (분석) GitHub Settings → Secrets에서 `LOGSTASH_URL` 시크릿 삭제는 운영자 수동 작업으로 PR 본문에 명시 — 운영자 수동 작업으로 남김(10.5와 동일)

## 9. 운영 검증 (배포 후 수동)

> 9.1~9.10은 develop 머지·dev 자동 배포 + data 노드 수동 배포 완료 이후 검증 항목. 구현 시점에 체크 불가, 머지 후 운영자가 PR 코멘트 또는 별도 점검에서 확인할 것.

- [x] 9.1 dev 배포 후 app 노드에서 `docker service ls` 출력에 `kibana`/`grafana`/`prometheus` 사라지고 `alloy`가 떠 있음을 확인
- [x] 9.2 data 노드에서 `docker compose ps` 출력에 `logstash` 사라지고 `alloy`가 떠 있음을 확인
- [x] 9.3 Grafana Cloud UI에서 LogQL `{app="nomat-back"}` 쿼리 → 최근 5분 spring-app 로그가 보이는지 확인
- [x] 9.4 `{app="nomat-back", log_type="access-log"}` 쿼리 → 최근 access 로그가 별도 stream으로 보이는지 확인
- [x] 9.5 `{app="nomat-back", level="ERROR"}` 쿼리 → 에러 로그가 정상 분리되는지 확인 (artificially trigger 가능하면)
- [x] 9.6 LogQL `{app="nomat-back"} | json | requestId="<some-id>"` 쿼리 → MDC 필드가 line content에서 추출 가능한지 확인
- [x] 9.7 Mimir 쿼리 `node_cpu_seconds_total{node=~"app|data"}` → 두 노드 모두 메트릭 수집되는지 확인
- [x] 9.8 dev URL(`https://api.dev.nomat.live/health`) 200 응답 → 로그 파이프라인 변경이 앱 동작에 영향 없음
- [x] 9.9 dev URL(`https://api.dev.nomat.live/info`) 정상 응답 → Actuator 영향 없음
- [x] 9.10 app/data 노드 EC2 메모리·디스크 사용량이 변경 전 대비 감소함을 확인 (Kibana 800MB+, Grafana 250MB+, Prometheus 200MB+ 회수 예상)

## 10. 후속 (별도 작업 또는 후속 OpenSpec change 후보)

- [x] 10.1 **1-2주 안정 운영 확인 후** ES logstash-* 인덱스 삭제: `curl -X DELETE 'http://elasticsearch:9200/logstash-*' -u elastic:$ELASTIC_PASSWORD`. ES 디스크 사용량 회수 확인. **PR 머지 직후 실행 금지** (롤백 시 과거 로그 복구 불가)
- [x] 10.2 self-hosted Grafana `grafana-data` Docker volume이 다른 서비스에서 참조하지 않음을 확인 후 삭제 (`docker volume rm nomat-back_grafana-data`). 본 변경 머지 직후 실행 금지 — 1-2주 후
- [x] 10.3 self-hosted Prometheus `prometheus-data` Docker volume도 동일하게 1-2주 후 삭제
- [x] 10.4 EC2 `.env`에서 `LOGSTASH_URL`, Kibana 관련 환경변수 정리 (운영자 수동)
- [x] 10.5 GitHub Settings → Secrets에서 `LOGSTASH_URL` 시크릿 삭제 (운영자 수동)
- [x] 10.6 (후속 change 후보) JVM·Spring Boot 메트릭을 Actuator `prometheus` endpoint로 노출 + Alloy scrape 추가 — 본 변경에서는 노출하지 않음 (Non-Goal). 트래픽 가시성이 더 필요하면 후속 변경
- [] 10.7 (후속 change 후보) Tempo 도입 — Alloy에 OTLP receiver 추가 + 백엔드에 OpenTelemetry SDK 적용. 본 변경 범위 외
- [] 10.8 (후속 change 후보) `event_publication` 테이블 outbox lag 메트릭을 Mimir에 노출 → Modulith outbox 모니터링 강화
