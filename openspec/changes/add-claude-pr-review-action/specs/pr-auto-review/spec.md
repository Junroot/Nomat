## ADDED Requirements

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
워크플로우의 GitHub 토큰 권한은 `pull-requests: write`와 `contents: read`만 명시해야 한다(MUST). 그 외 권한(예: `issues`, `id-token`, `actions`, `packages`)은 부여하지 않는다.

#### Scenario: 워크플로우 권한 블록 선언
- **WHEN** 워크플로우 또는 잡 레벨 `permissions:` 블록이 정의된다
- **THEN** `pull-requests: write`와 `contents: read`만 명시되며 다른 권한 항목은 누락 또는 `none`으로 유지된다

#### Scenario: 권한 미명시로 기본값이 적용되는 상황
- **WHEN** 저장소 기본 GITHUB_TOKEN 권한이 광범위하게 설정되어 있다
- **THEN** 워크플로우 파일이 자체 `permissions:` 블록으로 권한을 좁혀 액션 호출이 최소 권한으로 동작한다

### Requirement: 토큰 발급·갱신 운영 절차 문서화
저장소는 `CLAUDE_CODE_OAUTH_TOKEN` 발급·등록·갱신 절차를 README 또는 별도 운영 문서에 한국어로 명시해야 한다(SHALL).

#### Scenario: 신규 저장소 관리자가 토큰을 처음 등록
- **WHEN** 새로운 관리자가 자동 리뷰를 활성화하기 위해 토큰을 처음 설정한다
- **THEN** 문서에 명시된 절차(`claude setup-token` 실행 → GitHub Settings → Secrets and variables → Actions에 `CLAUDE_CODE_OAUTH_TOKEN` 등록)를 추가 안내 없이 따라 완료할 수 있다

#### Scenario: 토큰 만료로 자동 리뷰가 조용히 실패
- **WHEN** OAuth 토큰 만료로 액션이 실패하기 시작한다
- **THEN** 운영자는 저장소 문서에서 토큰 재발급(`claude setup-token`) 및 Secret 갱신 절차를 찾아 복구할 수 있다
