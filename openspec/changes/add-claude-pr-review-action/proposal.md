## Why

PR이 생성될 때마다 사람 리뷰어를 기다리는 동안 명백한 결함(코드 스타일, 누락된 테스트, 잠재 버그)을 조기에 발견하지 못해 리뷰 사이클이 길어진다. GitHub Actions에서 Claude Code를 자동 실행해 1차 리뷰 코멘트를 남기면 리뷰어 부담을 줄이고 작성자가 더 빠르게 피드백을 받을 수 있다.

## What Changes

- `.github/workflows/`에 PR 자동 리뷰 워크플로우 신규 추가 — `pull_request` 이벤트의 `opened`, `synchronize` 타입에 트리거되며 모든 PR을 대상으로 함
- 워크플로우는 공식 `anthropics/claude-code-action`(혹은 동등 액션)을 호출해 변경된 diff에 대한 리뷰 코멘트를 PR에 남김
- 인증은 **Claude Code OAuth 토큰** 방식 사용 — 로컬에서 `claude setup-token` 실행으로 발급한 토큰을 GitHub Secrets `CLAUDE_CODE_OAUTH_TOKEN`에 등록 (저장소 settings에서 수동 설정 — 별도 안내 필요). API 키 종량제 대신 Claude Pro/Max 구독 한도 내에서 사용
- 워크플로우에 동시성 제어(concurrency group) 적용 — 같은 PR에서 새 커밋이 푸시되면 진행 중인 리뷰는 취소하고 최신 커밋만 리뷰
- 백엔드·프론트엔드·인프라 어떤 변경 PR이든 동일 워크플로우로 리뷰 (별도 분기 불필요)

### 리뷰 결과 정책

- 리뷰 결과는 **non-blocking 코멘트**로 남긴다 — PR 체크 실패로 머지를 차단하지 않음 (false positive로 인한 작업 정지 방지)
- 기존 `back-pull-request.yml`(테스트), `front-pull-request.yml`(빌드/프리뷰)은 그대로 머지 게이팅 역할을 유지

## Capabilities

### New Capabilities

- `pr-auto-review`: GitHub Actions에서 Claude Code를 활용해 PR diff를 자동 리뷰하고 결과를 코멘트로 게시하는 CI 자동화 기능

### Modified Capabilities

(없음 — 기존 spec과 독립적인 신규 CI 워크플로우)

## Impact

- **저장소 가시성**: public 저장소 — fork PR은 secrets 접근 불가로 자동 리뷰가 동작하지 않음 (`pull_request` 이벤트 사용으로 인한 의도된 트레이드오프, fork PR은 maintainer 수동 리뷰)
- **인프라 / CI**: `.github/workflows/`에 신규 워크플로우 파일 1개 추가. 기존 `back-pull-request.yml`, `front-pull-request.yml` 등 PR 워크플로우와 독립 동작
- **GitHub Secrets**: `CLAUDE_CODE_OAUTH_TOKEN` 신규 등록 필요 (저장소 관리자 작업, `claude setup-token`으로 사전 발급)
- **GitHub 권한**: 워크플로우에 `pull-requests: write`, `contents: read` 권한 부여 필요
- **외부 계정 의존성**: Claude Pro/Max 구독 계정 필요 — 무료 계정으로는 OAuth 토큰 발급 불가
- **사용량 제한**: 별도 API 비용 대신 Pro/Max 구독 한도 내에서 사용 — 한도 초과 시 rate limit 적용 (PR 리뷰 일시 실패 가능)
- **토큰 만료 운영**: OAuth 토큰은 만료가 있어 주기적 갱신 필요 — 만료 시 워크플로우가 조용히 실패할 수 있음
- **백엔드 / 프론트엔드 코드**: 영향 없음 (애플리케이션 코드 변경 없음)
- **DB / ES / Kafka / Redis**: 영향 없음
