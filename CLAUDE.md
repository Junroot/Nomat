# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

서브디렉토리별 상세 가이드는 `back/CLAUDE.md`, `front/CLAUDE.md`를 참조하세요.

## 프로젝트 개요

Nomat은 노래 맞히기 게임 애플리케이션이다. 사용자는 Discord OAuth2로 인증하고, YouTube 트랙 기반 플레이리스트를 생성/관리하며, 공유 감상 방에 참여한다. 모노레포 구조로 백엔드, 프론트엔드, 인프라 코드를 포함한다.

## 저장소 구조

```
back/     — Kotlin/Spring Boot 백엔드 API
front/    — React SPA 프론트엔드
infra/    — Docker Compose, Nginx, Prometheus, Kibana 등 인프라 설정
  app/    — 프로덕션 Docker Swarm 스택 (compose.yml, nginx.conf)
  data/   — 데이터 서비스 스택 (MySQL, ES, Kafka, Redis, Logstash)
```

## 빌드 및 실행 명령어

### 백엔드 (`back/`)

```bash
./gradlew build                # 전체 빌드 (컴파일 + 테스트)
./gradlew test                 # 전체 테스트 실행
./gradlew test --tests "ilpak.nomat.playlist.in.PlaylistControllerTest"       # 단일 테스트 클래스
./gradlew test --tests "ilpak.nomat.playlist.in.PlaylistControllerTest.save"  # 단일 테스트 메서드
./gradlew detekt               # Kotlin 정적 분석 (ignoreFailures=true)
./gradlew bootRun --args='--spring.profiles.active=local'  # 로컬 실행 (Testcontainers)
```

### 프론트엔드 (`front/`)

```bash
npm run dev        # 개발 서버 (http://localhost:5173)
npm run build      # 프로덕션 빌드
npm run typecheck  # react-router typegen + tsc
npm run start      # 프로덕션 빌드 서빙
```

테스트 프레임워크는 설정되어 있지 않음.

## 기술 스택

- **백엔드**: Kotlin 1.9 / Spring Boot 3.4 / Java 17 / Gradle
- **프론트엔드**: React 19 / React Router v7 (SPA) / Vite 5 / TypeScript / Tailwind CSS v4 / Zustand
- **데이터**: MySQL 8 + Flyway, Elasticsearch 9 (Nori 한국어 분석기), Redis, Kafka
- **인증**: Discord OAuth2 + JWT
- **CDC**: Debezium 임베디드 (MySQL → Kafka → Elasticsearch 플레이리스트 동기화)
- **배포**: Docker Swarm (백엔드, EC2), Netlify (프론트엔드)

## 아키텍처

### 백엔드 — 헥사고날 아키텍처

기본 패키지: `ilpak.nomat`. 도메인 모듈: `playlist`, `room`, `player`, `favoriteplaylist`, `auth`.

```
module/
├── in/              # 인바운드 어댑터 (REST 컨트롤러, 이벤트 리스너, Redis 구독자 등) — private class
├── out/             # 아웃바운드 어댑터 (저장소 구현체) — private class
└── application/
    ├── domain/      # JPA 엔티티 + 저장소 인터페이스 (포트) + 도메인 이벤트
    ├── dto/         # Request/Response DTO
    └── *Service.kt  # 비즈니스 로직
```

컨트롤러와 저장소 구현체는 **`private class`** — 패키지 외부에서 직접 참조 불가. 도메인 이벤트는 `AbstractAggregateRoot` + `@TransactionalEventListener(AFTER_COMMIT)` 패턴 사용. 횡단 관심사는 `infrastructure/` 패키지에 위치 (security, web, redis, cdc, container, jpa, elasticsearch).

### 프론트엔드 — React SPA

라우트는 `app/routes.ts`에서 수동 정의. `~/`는 `./app/`으로 매핑되는 경로 별칭. SVG는 `?react` 접미사로 컴포넌트 임포트. API 호출은 `app/utils/api.ts`의 Axios 클라이언트를 통해 중앙 관리.

## 프로파일 및 환경

- **local / test**: Testcontainers로 모든 외부 의존성 자동 구성 — 자격 증명 불필요
- **dev**: 환경 변수로 외부 서비스 연결 (SPRING_DATASOURCE_*, ELASTICSEARCH_*, KAFKA_*, JWT_KEY 등)
- 프론트엔드 API URL: `.env.development.local` → `http://localhost:8080`, `.env.development` → `https://api.dev.nomat.live`

## CI/CD

- **백엔드 PR**: `./gradlew test` + Detekt (reviewdog로 PR 코멘트)
- **백엔드 develop push**: Docker 이미지 빌드 → Docker Hub push → EC2 SSH 배포 (docker stack deploy)
- **프론트엔드 PR**: 빌드 + Netlify 프리뷰 배포
- **프론트엔드 develop push**: Netlify 프로덕션 배포
- **인프라 develop push**: EC2에 infra/app 설정 동기화 후 docker stack deploy

## 컨벤션

- 커밋 메시지는 한국어 Conventional Commits: `feat:`, `fix:` 등
- UI 텍스트 및 에러 메시지는 전체 한국어
- 메인 브랜치는 **`develop`**
- 다크 테마 — Tailwind zinc 팔레트, cyan-400 액센트
