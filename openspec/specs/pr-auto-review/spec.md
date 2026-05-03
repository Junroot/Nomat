# pr-auto-review Specification

## Purpose

GitHub PR이 열리거나 갱신될 때 Claude Code가 자동으로 코드 리뷰를 수행하고 결과를 PR 코멘트로 게시하는 워크플로우 역량을 정의한다. 머지 게이팅과 분리된 비차단 보조 리뷰로, 공식 `anthropics/claude-code-action`과 OAuth 토큰 기반 인증을 사용하며 최소 권한 원칙을 따른다.

## Requirements

### Requirement: PR 이벤트 자동 트리거
시스템은 PR이 새로 열리거나(`opened`) head 브랜치에 새 커밋이 푸시될 때(`synchronize`) GitHub Actions에서 Claude Code 자동 리뷰 워크플로우를 실행해야 한다(SHALL). 트리거는 `pull_request` 이벤트만 사용하며 `pull_request_target`은 사용하지 않는다.

#### Scenario: PR이 새로 열림
- **WHEN** 작성자가 develop 브랜치를 base로 새 PR을 연다
- **THEN** GitHub Actions가 `pull_request` 이벤트의 `opened` 타입에 매칭되어 자동 리뷰 워크플로우를 실행한다

#### Scenario: 기존 PR head 브랜치에 새 커밋이 푸시됨
- **WHEN** 이미 열린 PR의 head 브랜치에 새 커밋이 푸시된다
- **THEN** GitHub Actions가 `pull_request` 이벤트의 `synchronize` 타입으로 워크플로우를 다시 실행한다

#### Scenario: fork 저장소에서 올라온 PR
- **WHEN** 외부 fork 저장소에서 PR이 열린다
- **THEN** 워크플로우 실행 컨텍스트에서 `CLAUDE_CODE_OAUTH_TOKEN` secret에 접근할 수 없어 자동 리뷰가 동작하지 않으며, fork PR은 maintainer 수동 리뷰 대상으로 남는다

### Requirement: 공식 Claude Code 액션을 통한 리뷰 게시
워크플로우는 공식 `anthropics/claude-code-action` GitHub Action을 호출해 PR diff를 분석하고 결과를 PR 코멘트로 게시해야 한다(MUST). 액션 버전은 메이저 태그 또는 SHA로 핀 고정한다.

#### Scenario: 워크플로우 실행 시 공식 액션 호출
- **WHEN** 자동 리뷰 워크플로우의 잡이 실행된다
- **THEN** 잡 단계에서 `anthropics/claude-code-action`을 호출해 GitHub PR API를 통해 변경 diff에 대한 리뷰 코멘트를 게시한다

#### Scenario: 액션 버전이 메이저 태그로 핀 고정됨
- **WHEN** 워크플로우 YAML에서 액션 참조를 작성한다
- **THEN** `uses: anthropics/claude-code-action@<major-tag-or-sha>` 형식으로 버전을 명시해 임의 업데이트로 워크플로우가 깨지지 않게 한다

### Requirement: OAuth 토큰 기반 Claude 인증
액션은 GitHub Secrets에 저장된 `CLAUDE_CODE_OAUTH_TOKEN`을 사용해 Claude에 인증해야 한다(MUST). Anthropic API 키 종량제 인증은 사용하지 않는다.

#### Scenario: 유효한 OAuth 토큰으로 인증
- **WHEN** 워크플로우가 실행되고 secret `CLAUDE_CODE_OAUTH_TOKEN`에 유효한 값이 등록되어 있다
- **THEN** 액션이 해당 토큰으로 Claude에 인증해 리뷰를 수행하며 비용은 토큰 소유자의 Claude Pro/Max 구독 한도에서 차감된다

#### Scenario: 토큰이 만료되거나 누락된 상태
- **WHEN** secret이 비어 있거나 만료되어 인증이 실패한다
- **THEN** 액션 단계가 실패로 종료되지만 워크플로우는 머지 게이팅이 아니므로 PR 머지 자체는 차단되지 않는다

#### Scenario: 구독 한도(rate limit) 초과
- **WHEN** Claude Pro/Max 구독 한도를 초과해 액션 호출이 거부된다
- **THEN** 해당 PR의 자동 리뷰는 누락되지만 일시적 실패로 분류되며 PR 머지는 차단되지 않는다

### Requirement: PR 단위 동시성 제어
워크플로우는 같은 PR에 대해 동시에 한 번만 실행되도록 제어하고 새 커밋이 들어오면 진행 중인 이전 실행을 취소해야 한다(SHALL).

#### Scenario: 같은 PR에 연속 push 발생
- **WHEN** 동일 PR의 head 브랜치에 push가 연달아 들어와 이전 리뷰 실행이 아직 진행 중이다
- **THEN** `concurrency.group=claude-pr-review-${{ github.event.pull_request.number }}`와 `cancel-in-progress: true` 설정으로 이전 실행이 취소되고 최신 커밋 기준 리뷰만 남는다

#### Scenario: 서로 다른 PR이 동시에 트리거됨
- **WHEN** 서로 다른 두 PR이 동시에 `opened` 또는 `synchronize` 이벤트로 트리거된다
- **THEN** concurrency 그룹 키가 PR 번호별로 달라 두 워크플로우가 서로를 막지 않고 병렬로 실행된다

### Requirement: 머지 비차단(non-blocking) 리뷰
자동 리뷰의 성공/실패는 PR 머지를 차단하지 않아야 한다(MUST). 결과는 PR 코멘트로만 게시되며 머지 게이팅은 기존 `back-pull-request.yml`·`front-pull-request.yml`이 담당한다.

#### Scenario: 액션 실행 실패
- **WHEN** 토큰 만료·rate limit·액션 입력 스키마 변경 등으로 액션 단계가 실패한다
- **THEN** 워크플로우는 실패로 표시되지만 GitHub의 필수 상태 체크에 등록되지 않아 PR은 머지 가능하다

#### Scenario: Claude가 false positive 코멘트를 남김
- **WHEN** Claude가 사실과 다른 결함을 지적한다
- **THEN** 작성자는 코멘트를 무시하거나 답변만 남기고 머지를 그대로 진행할 수 있다

### Requirement: 모든 변경 PR을 동일하게 리뷰
워크플로우는 `paths` 필터를 두지 않고 어떤 디렉터리 변경 PR이든 동일하게 리뷰해야 한다(SHALL).

#### Scenario: 백엔드만 변경된 PR
- **WHEN** PR이 `back/` 디렉터리 파일만 수정한다
- **THEN** 워크플로우가 트리거되어 변경 diff에 대한 리뷰 코멘트가 게시된다

#### Scenario: 프론트엔드만 변경된 PR
- **WHEN** PR이 `front/` 디렉터리 파일만 수정한다
- **THEN** 워크플로우가 트리거되어 변경 diff에 대한 리뷰 코멘트가 게시된다

#### Scenario: 인프라·문서·여러 디렉터리가 섞인 PR
- **WHEN** PR이 `infra/`·README·`back/`·`front/` 등 임의의 경로를 동시 수정한다
- **THEN** 단일 워크플로우가 트리거되어 cross-cutting 컨텍스트를 함께 보고 리뷰 코멘트를 게시한다

### Requirement: 최소 권한 원칙 적용
워크플로우의 GitHub 토큰 권한은 액션 동작에 필요한 최소 항목으로만 좁혀야 한다(MUST). 구체적으로 `contents: read`(소스·diff 읽기), `pull-requests: write`(코멘트 게시), `id-token: write`(`anthropics/claude-code-action` 공식 예제 워크플로우의 권장 디폴트로, OIDC 기반 외부 서비스 인증 호환성을 위해 포함)만 명시한다. 그 외 권한(예: `issues`, `actions`, `packages`)은 부여하지 않는다.

#### Scenario: 워크플로우 권한 블록 선언
- **WHEN** 워크플로우 또는 잡 레벨 `permissions:` 블록이 정의된다
- **THEN** `contents: read`, `pull-requests: write`, `id-token: write` 세 항목만 명시되며 다른 권한 항목은 누락 또는 `none`으로 유지된다

#### Scenario: 권한 미명시로 기본값이 적용되는 상황
- **WHEN** 저장소 기본 GITHUB_TOKEN 권한이 광범위하게 설정되어 있다
- **THEN** 워크플로우 파일이 자체 `permissions:` 블록으로 권한을 좁혀 액션 호출이 위 세 항목으로만 동작한다

### Requirement: 토큰 발급·갱신 운영 절차 문서화
저장소는 `CLAUDE_CODE_OAUTH_TOKEN` 발급·등록·갱신 절차를 README 또는 별도 운영 문서에 한국어로 명시해야 한다(SHALL).

#### Scenario: 신규 저장소 관리자가 토큰을 처음 등록
- **WHEN** 새로운 관리자가 자동 리뷰를 활성화하기 위해 토큰을 처음 설정한다
- **THEN** 문서에 명시된 절차(`claude setup-token` 실행 → GitHub Settings → Secrets and variables → Actions에 `CLAUDE_CODE_OAUTH_TOKEN` 등록)를 추가 안내 없이 따라 완료할 수 있다

#### Scenario: 토큰 만료로 자동 리뷰가 조용히 실패
- **WHEN** OAuth 토큰 만료로 액션이 실패하기 시작한다
- **THEN** 운영자는 저장소 문서에서 토큰 재발급(`claude setup-token`) 및 Secret 갱신 절차를 찾아 복구할 수 있다

### Requirement: 자동 리뷰 가이드라인 문서 존재
저장소 루트에 자동 리뷰 가이드라인 문서 `REVIEW.md`가 존재해야 한다(MUST). 이 문서는 자동 리뷰 동작의 단일 소스(single source of truth)이며, 워크플로우 YAML이 직접 인라인하지 않은 페르소나·정책·예시·컨벤션 컨텍스트를 모두 담는다. 문서가 누락되면 워크플로우의 `prompt:` 지시문이 끊겨 자동 리뷰 품질이 보장되지 않는다.

#### Scenario: 가이드라인 문서가 저장소 루트에 위치
- **WHEN** 자동 리뷰 워크플로우가 트리거되어 액션 에이전트가 저장소를 체크아웃한다
- **THEN** 저장소 루트의 `REVIEW.md`를 `Read` 도구로 즉시 접근할 수 있다

#### Scenario: 가이드라인 문서 누락 감지
- **WHEN** 누군가 `REVIEW.md`를 삭제하거나 다른 경로로 이동시키는 PR을 연다
- **THEN** OpenSpec 검증 단계 또는 자체 PR 리뷰 단계에서 `REVIEW.md` 부재가 spec 위반으로 드러나 머지 전에 차단된다

### Requirement: 가이드라인 문서의 핵심 항목 포함
`REVIEW.md`는 자동 리뷰 품질을 결정짓는 다음 핵심 항목을 모두 포함해야 한다(SHALL).

- **페르소나 정의**: 시니어 코드 리뷰어 역할 부여 문구.
- **심각도 라벨 체계**: 🔴 Important / 🟡 Nit / 🟣 Pre-existing 3단계 정의 — 각 라벨의 적용 기준 포함.
- **🔴 vs 🟡 경계 예시**: "테스트 누락" 등 모호한 영역의 구체 분류 예시 — 가능하면 기존 테스트 코드(`back/src/test/...`)의 `file:line` 인용 포함.
- **High-signal 필터(코멘트 금지 목록)**: Detekt·TypeScript 컴파일러·Flyway가 이미 잡는 항목, 입력 의존적 가설성 결함, 단순 스타일 nitpick.
- **증거 인용 의무**: 모든 결함 코멘트에 `file:line` 인용을 강제한다는 규칙.
- **Nit cap**: 한 리뷰당 🟡 코멘트 5개 상한 및 초과분 처리 방식.
- **결과 없음 포맷**: 결함 미발견 시 게시할 단일 확인 코멘트의 고정 형식.
- **출력 형식 분리**: 인라인 코멘트(라인 단위 결함)와 상위 코멘트(요약·카운트)의 사용 규칙.
- **프로젝트 컨벤션 컨텍스트**: 백엔드 헥사고날 모듈 목록(`playlist`, `room`, `player`, `favoriteplaylist`, `auth`)·`in/out/application` 계층·`private class`·도메인 이벤트 패턴(`AbstractAggregateRoot` + `@TransactionalEventListener(AFTER_COMMIT)`)·React Router v7·Zustand·Tailwind zinc 다크 테마 등 핵심 컨벤션.

#### Scenario: 심각도 라벨 체계 명시
- **WHEN** 가이드라인을 읽는다
- **THEN** 🔴 Important / 🟡 Nit / 🟣 Pre-existing 3단계 라벨 정의와 각 라벨의 적용 기준을 찾을 수 있다

#### Scenario: High-signal 필터 명시
- **WHEN** 가이드라인을 읽는다
- **THEN** Detekt·TypeScript 컴파일러·Flyway가 이미 검출하는 항목과 입력 의존적 가설성 결함을 코멘트 금지 목록으로 명시한 섹션을 찾을 수 있다

#### Scenario: 증거 인용 의무 명시
- **WHEN** 가이드라인을 읽는다
- **THEN** 모든 결함 코멘트가 `file:line` 형식의 위치 인용을 포함해야 하며 인용 위치가 없으면 코멘트를 만들지 않는다는 규칙을 찾을 수 있다

#### Scenario: Nit cap 명시
- **WHEN** 가이드라인을 읽는다
- **THEN** 🟡 Nit 코멘트가 한 리뷰당 5건을 넘을 경우 초과분을 인라인으로 만들지 않고 상위 요약 코멘트의 카운트로만 표기한다는 규칙을 찾을 수 있다

#### Scenario: 결과 없음 포맷 명시
- **WHEN** 가이드라인을 읽는다
- **THEN** 결함을 발견하지 못한 PR에 게시할 짧은 단일 상위 코멘트의 고정 형식을 찾을 수 있다

#### Scenario: 인라인 vs 상위 코멘트 출력 규칙 명시
- **WHEN** 가이드라인을 읽는다
- **THEN** 라인 단위 결함은 인라인 코멘트로, PR 전반의 요약·카운트·결과 없음 알림은 상위 코멘트로 분리한다는 규칙을 찾을 수 있다

#### Scenario: 프로젝트 컨벤션 컨텍스트 포함
- **WHEN** 가이드라인을 읽는다
- **THEN** 백엔드 헥사고날 모듈 목록과 `in/out/application` 계층 구조, `private class` 강제, 도메인 이벤트 패턴, 프론트엔드 React Router v7·Zustand·Tailwind 컨벤션이 컨텍스트로 명시된 섹션을 찾을 수 있다

#### Scenario: 🔴 vs 🟡 경계 예시 포함
- **WHEN** 가이드라인을 읽는다
- **THEN** "테스트 누락"처럼 분류가 모호한 영역에 대해 🔴와 🟡를 구분하는 구체 예시(가능하면 기존 테스트 코드의 `file:line` 인용 포함)를 찾을 수 있다

### Requirement: 워크플로우의 가이드라인 참조
`.github/workflows/claude-pr-review.yml`의 `prompt:` 블록은 자동 리뷰 가이드라인 문서를 명시적으로 참조해야 한다(MUST). 워크플로우는 가이드라인 본문을 인라인하지 않고 PR 컨텍스트(저장소·PR 번호)와 가이드라인을 읽으라는 명령형 지시문만 둔다 — 두 파일의 동기화를 spec 차원에서 강제해 가이드라인 단독 삭제·이름 변경으로 참조가 깨지는 것을 차단한다.

#### Scenario: 워크플로우 prompt 블록이 가이드라인을 참조
- **WHEN** `.github/workflows/claude-pr-review.yml`의 `prompt:` 블록을 읽는다
- **THEN** 첫 머리에 "저장소 루트의 `REVIEW.md`를 먼저 읽고 그 가이드 전체를 따라 리뷰하라"는 의미의 명령형 지시문이 명시되어 있다

#### Scenario: 워크플로우가 가이드라인 본문을 인라인하지 않음
- **WHEN** 워크플로우 YAML의 `prompt:` 블록을 읽는다
- **THEN** 심각도 라벨·필터 규칙·컨벤션 컨텍스트 등 가이드라인 본문이 워크플로우 안에 직접 인라인되어 있지 않으며 모든 정책은 `REVIEW.md` 한 곳에서만 관리된다

### Requirement: Pre-existing 결함 판정용 이슈 조회 도구 허용
워크플로우의 `claude_args.--allowedTools` 목록은 자동 리뷰 에이전트가 🟣 Pre-existing 라벨의 근거를 확보할 수 있도록 read-only 이슈 검색 명령을 포함해야 한다(SHALL). 구체적으로 `Bash(gh search:*)`와 `Bash(gh issue list:*)`를 추가하며, 코멘트 게시·라벨 변경 같은 쓰기 작업은 허용하지 않는다.

#### Scenario: 이슈 검색 도구가 허용 목록에 포함
- **WHEN** 워크플로우 YAML의 `claude_args.--allowedTools` 목록을 읽는다
- **THEN** `Bash(gh search:*)`와 `Bash(gh issue list:*)`가 명시되어 있어 자동 리뷰 에이전트가 GitHub 이슈/PR을 read-only로 조회할 수 있다

#### Scenario: 쓰기 권한은 확장하지 않음
- **WHEN** 워크플로우 YAML의 `claude_args.--allowedTools` 목록을 읽는다
- **THEN** `Bash(gh:*)` 같은 와일드카드 일괄 허용은 사용하지 않으며 머지 트리거·라벨 변경·이슈 생성 등 쓰기 작업을 수행하는 명령은 포함되지 않는다

#### Scenario: Pre-existing 라벨 판정 시 이슈 컨텍스트 조회
- **WHEN** 자동 리뷰 에이전트가 PR diff에서 발견한 결함이 기존 코드에서 비롯된 것인지 판단해야 한다
- **THEN** 에이전트는 허용된 `gh search` / `gh issue list` 명령으로 관련 이슈를 조회하고 결과를 근거로 🟣 Pre-existing 라벨을 부여한다
