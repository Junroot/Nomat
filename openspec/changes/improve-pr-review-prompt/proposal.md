## Why

현재 `.github/workflows/claude-pr-review.yml`의 리뷰 프롬프트는 5개 항목(버그·테스트·보안·코드 품질·일관성)을 단순 나열한 1차 버전이다. Anthropic 공식 Code Review 플러그인과 업계 LLM 코드 리뷰 best practice가 공통으로 강조하는 요소 — **High-signal 필터(false positive 억제), 심각도 분류, 증거 인용 의무, 운영적 노이즈 제어(nit cap, 재리뷰 수렴), Detekt·typecheck가 이미 잡는 항목 제외** — 가 빠져 있어 노이즈성 코멘트가 누적될 위험이 크다. 또한 우리 프로젝트의 헥사고날 컨벤션(`private class`, `AbstractAggregateRoot`+`@TransactionalEventListener(AFTER_COMMIT)` 등)·React SPA 컨벤션을 프롬프트에 명시하지 않아 일관성 위반을 충분히 잡지 못한다.

운영을 시작한 직후이므로 코멘트 패턴이 굳기 전에 best practice를 반영해 프롬프트 품질을 끌어올리는 것이 비용 대비 효과가 가장 크다.

## What Changes

- 저장소 루트에 `REVIEW.md` 신규 추가 — 자동 리뷰 가이드라인 본문(페르소나·심각도 라벨·필터·인용 의무·nit cap·출력 형식·컨벤션 컨텍스트·🔴/🟡 경계 예시)을 마크다운으로 작성
  - **Persona** 명시 (시니어 리뷰어 역할 부여)
  - **심각도 라벨** 도입: 🔴 Important / 🟡 Nit / 🟣 Pre-existing — 공식 Code Review 플러그인과 동일 체계
  - **High-signal 필터 규칙**: Detekt·TypeScript 컴파일러·Flyway가 이미 잡는 항목, 입력 의존적 가설성 결함, 단순 스타일 nitpick은 코멘트 금지
  - **증거 인용 의무**: 결함 지적 시 `file:line` 인용을 요구해 인라인 코멘트의 정확도를 강제
  - **Nit cap**: 한 리뷰당 🟡 Nit 코멘트 5개 상한, 초과분은 상위 코멘트 요약에서 카운트로만 표기
  - **결과 없음 포맷** 표준화: 결함 미발견 시 짧은 확인 코멘트 형식 명시
  - **출력 형식** 명시: 인라인 코멘트(라인별 결함) vs 상위 코멘트(요약·카운트) 구분 규칙
  - **프로젝트 컨벤션 컨텍스트** 주입: 백엔드 헥사고날 패키지·`private class`·도메인 이벤트 패턴, 프론트 React Router v7·Zustand·Tailwind zinc 컨벤션 핵심 항목을 본문에 명시
  - **🔴 vs 🟡 경계 예시** 첨부: 동시성·세션·인증·트랜잭셔널 이벤트 누락은 🔴, DTO Bean Validation/단순 DTO 매핑/이미 커버된 happy path 변형 누락은 🟡 — 기존 테스트 코드에서 도출한 인용 포함
- `.github/workflows/claude-pr-review.yml`의 `prompt` 블록을 짧게 유지 — "먼저 `REVIEW.md`를 읽고 그 가이드에 따라 리뷰하라"는 지시문 + PR 컨텍스트만 남김
- `claude_args.--allowedTools`에 `Bash(gh search:*)`, `Bash(gh issue list:*)` 추가 (이슈 컨텍스트 조회 허용 — 🟣 Pre-existing 라벨 판정 근거)
- `openspec/changes/add-claude-pr-review-action/specs/pr-auto-review/spec.md`에 **자동 리뷰 프롬프트 품질 요구사항**을 신규 Requirement로 추가 — `REVIEW.md` 존재·핵심 항목 포함·워크플로우의 `REVIEW.md` 참조를 spec 차원에서 강제해 프롬프트 회귀를 차단

### Non-Goals

- 외부 LLM 평가 인프라(별도 평가 스크립트로 프롬프트 회귀 자동 테스트)는 도입하지 않는다 — 회귀 방지는 spec Requirement로 충분.
- 모델 교체·Claude Code Action 버전 업그레이드는 다루지 않는다.
- fork PR 지원, 머지 게이팅 전환, Anthropic Code Review 관리형 서비스로의 마이그레이션은 다루지 않는다.

## Capabilities

### New Capabilities

(없음)

### Modified Capabilities

- `pr-auto-review`: 자동 리뷰 프롬프트의 **품질 기준**(심각도 분류, high-signal 필터, 증거 인용, nit cap, 컨벤션 컨텍스트)을 신규 요구사항으로 추가. 트리거·인증·동시성·권한 등 기존 요구사항은 변경하지 않음.

> 참고: `pr-auto-review` 스펙은 아직 `openspec/specs/`에 동기화되지 않았고 활성 변경 `add-claude-pr-review-action`의 delta로만 존재한다. 이 변경의 delta는 `add-claude-pr-review-action`이 아카이브된 이후 `openspec/specs/pr-auto-review/spec.md` 기준으로 적용되도록 설계한다.

## Impact

- **인프라 / CI**: `.github/workflows/claude-pr-review.yml` 1개 파일 수정 + 저장소 루트에 `REVIEW.md` 1개 파일 신규 추가. 다른 워크플로우(`back-pull-request.yml`, `front-pull-request.yml`, 인프라 배포)는 영향 없음.
- **GitHub Actions 권한**: 변경 없음 (`contents: read`, `pull-requests: write`, `id-token: write` 그대로).
- **Secrets**: 변경 없음 (`CLAUDE_CODE_OAUTH_TOKEN` 그대로 사용).
- **백엔드(`back/`) / 프론트엔드(`front/`) 코드**: 영향 없음. 단, 프롬프트가 헥사고날 모듈(`playlist`, `room`, `player`, `favoriteplaylist`, `auth`)과 `in/out/application` 계층, `private class` 컨벤션, 도메인 이벤트 패턴을 인지하고 일관성 검사를 강화한다.
- **DB / ES / Kafka / Redis**: 영향 없음 (스키마·매핑·토픽·키 변경 없음).
- **OpenSpec 산출물**: `openspec/changes/improve-pr-review-prompt/` 신규 4개 아티팩트 생성. `add-claude-pr-review-action`의 spec delta에 새 Requirement가 추가됨 (해당 변경이 아직 활성 상태이므로 정렬 필요).
- **운영 비용**: 프롬프트가 길어져 입력 토큰이 증가하지만, high-signal 필터·nit cap·결과 없음 포맷으로 출력 토큰과 코멘트 노이즈가 감소해 순 효과는 중립~감소로 예상. 첫 N개 PR에서 토큰 사용량과 false positive 비율을 관찰해 후속 튜닝.
- **이해관계자**: PR 작성자(코멘트 노이즈 감소), 사람 리뷰어(자동 코멘트 신뢰도 상승), 저장소 관리자(spec으로 회귀 방지).
