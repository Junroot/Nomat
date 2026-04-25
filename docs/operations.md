# 운영 가이드

## Claude PR 자동 리뷰 (`.github/workflows/claude-pr-review.yml`)

PR이 열리거나(`opened`) head 브랜치에 새 커밋이 푸시되면(`synchronize`) GitHub Actions가 [`anthropics/claude-code-action`](https://github.com/anthropics/claude-code-action)을 호출해 변경 diff에 대한 1차 리뷰 코멘트를 PR에 남긴다.

### 동작 범위와 한계

- **머지 비차단(non-blocking)**: 자동 리뷰의 성공·실패는 PR 머지를 막지 않는다. 머지 게이팅은 기존 `back-pull-request.yml`(백엔드 테스트)과 `front-pull-request.yml`(프론트엔드 빌드/Netlify 프리뷰)이 담당한다. 자동 리뷰가 실패해도 PR은 정상 머지 가능하다.
- **fork PR 미지원**: 본 저장소는 public이고 워크플로우는 `pull_request` 이벤트만 사용하므로, 외부 fork에서 올라온 PR은 GitHub Actions의 보안 모델상 secrets에 접근할 수 없어 자동 리뷰가 동작하지 않는다. fork PR은 maintainer가 수동으로 리뷰한다. (`pull_request_target`은 임의 코드 실행 위험 때문에 의도적으로 사용하지 않는다.)
- **모든 경로 트리거**: `paths` 필터를 두지 않아 `back/`, `front/`, `infra/`, 문서 등 어떤 디렉터리 변경 PR이든 동일하게 리뷰한다.
- **PR 단위 동시성 제어**: 같은 PR에 새 커밋이 들어오면 진행 중이던 이전 실행은 취소되고 최신 커밋만 리뷰된다 (`concurrency.group=claude-pr-review-<PR번호>`, `cancel-in-progress: true`). 서로 다른 PR끼리는 병렬로 실행된다.

### 사전 준비 (저장소 관리자 1회 작업)

1. **OAuth 토큰 발급** — Claude Pro 또는 Max 구독 계정으로 로컬에서 다음 명령을 실행한다.

   ```bash
   claude setup-token
   ```

   브라우저로 Anthropic 인증을 마치면 터미널에 OAuth 토큰이 출력된다. 이 토큰은 발급한 사용자 계정의 Pro/Max 구독 한도를 사용한다 (별도 종량 과금 없음).

2. **GitHub Secrets 등록** — GitHub 저장소에서 Settings → Secrets and variables → Actions → "New repository secret" 클릭 후 다음 값으로 등록한다.
   - **Name**: `CLAUDE_CODE_OAUTH_TOKEN`
   - **Secret**: 1단계에서 발급받은 토큰 값

3. **워크플로우 권한 확인** — Settings → Actions → General → "Workflow permissions" 영역에서 워크플로우의 `pull-requests: write` 권한이 차단되지 않도록 기본 권한 설정을 확인한다. (워크플로우 파일이 `permissions:` 블록으로 직접 권한을 명시하므로, 저장소 기본값이 더 좁아도 워크플로우 안에서 필요한 권한만 부여된다.)

### 토큰 갱신 절차

OAuth 토큰은 만료가 있다. 만료되면 액션 단계가 인증 실패로 종료되며, 머지 게이팅이 아니라 별도 알림이 자동으로 뜨지 않으므로 조용히 누적될 수 있다. 다음 절차로 갱신한다.

1. 로컬에서 `claude setup-token`을 다시 실행해 새 토큰을 발급받는다.
2. GitHub 저장소 Settings → Secrets and variables → Actions → `CLAUDE_CODE_OAUTH_TOKEN` → "Update secret"으로 토큰 값을 교체한다.
3. 임의의 PR을 다시 트리거(빈 커밋 푸시 등)해 액션이 정상 동작하는지 확인한다.

### 액션 실패 시 운영 대응

| 증상 | 원인 후보 | 영향 | 대응 |
| --- | --- | --- | --- |
| 액션 단계가 401/403 인증 오류로 실패 | OAuth 토큰 만료 또는 누락 | PR 머지에는 영향 없음 (non-blocking) | 위 "토큰 갱신 절차" 수행 |
| 액션이 rate limit 오류로 실패 | Pro/Max 구독 한도 초과 | 해당 PR 자동 리뷰만 누락, 머지 영향 없음 | 일시적 현상이면 다음 커밋에서 자연 회복; 반복되면 발급 계정의 다른 사용량을 점검 |
| 액션 입력 스키마 오류로 실패 | 액션 메이저 버전 업데이트로 인한 입력 변경 | 자동 리뷰 누락, 머지 영향 없음 | [`anthropics/claude-code-action`](https://github.com/anthropics/claude-code-action) 릴리즈 노트를 확인하고 워크플로우 YAML의 `with:` 블록을 조정 |
| 워크플로우가 아예 트리거되지 않음 | Settings → Actions가 비활성화되었거나 워크플로우 권한이 잠김 | 자동 리뷰 비활성 | 저장소 Actions 설정과 워크플로우 권한 확인 |

자동 리뷰 자체를 일시적으로 끄고 싶다면 워크플로우 파일을 삭제하거나 `on:` 트리거를 일시적으로 제한하면 된다 — 머지 게이팅이 아니므로 다른 워크플로우에 영향을 주지 않는다.
