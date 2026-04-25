## 1. 사전 준비 (저장소 관리자 1회 작업)

- [x] 1.1 로컬에서 `claude setup-token` 실행해 OAuth 토큰을 발급받는다 (Claude Pro/Max 구독 계정 사용)
- [x] 1.2 GitHub 저장소 Settings → Secrets and variables → Actions에서 `CLAUDE_CODE_OAUTH_TOKEN` secret을 신규 등록한다
- [x] 1.3 저장소 Settings → Actions → General에서 워크플로우의 `pull-requests: write` 권한이 차단되지 않도록 기본 권한 설정을 확인한다

## 2. infra: PR 자동 리뷰 워크플로우 추가

- [x] 2.1 `.github/workflows/claude-pr-review.yml` 신규 작성 — `on: pull_request`, `types: [opened, synchronize]` 트리거 정의 (`pull_request_target` 미사용)
- [x] 2.2 워크플로우 또는 잡 레벨 `permissions:` 블록에 `contents: read`, `pull-requests: write`, `id-token: write`만 명시 (그 외 권한 부여하지 않음)
- [x] 2.3 PR 단위 동시성 제어 추가 — `concurrency.group: claude-pr-review-${{ github.event.pull_request.number }}` + `cancel-in-progress: true`
- [x] 2.4 잡 단계에서 `anthropics/claude-code-action`을 호출하고 `CLAUDE_CODE_OAUTH_TOKEN` secret을 입력으로 주입 — 액션 버전은 메이저 태그 또는 SHA로 핀 고정
- [x] 2.5 `paths` 필터를 두지 않아 모든 디렉터리(back/front/infra/문서) 변경 PR이 동일하게 리뷰되는지 워크플로우 정의에서 확인
- [x] 2.6 워크플로우 파일이 GitHub Actions의 YAML 스키마에 맞는지 `yamllint` 또는 GitHub 웹 UI 액션 편집기로 구문 검증

## 3. 문서화

- [x] 3.1 README 또는 `docs/operations.md` 신규 작성 — `claude setup-token` 발급 절차, GitHub Secrets 등록 절차, OAuth 토큰 만료 시 재발급·갱신 절차를 한국어로 명시
- [x] 3.2 fork 저장소 PR은 secrets 접근 불가로 자동 리뷰가 동작하지 않으며 maintainer 수동 리뷰 대상임을 문서에 명시
- [x] 3.3 자동 리뷰는 non-blocking 코멘트이며 머지 게이팅은 기존 `back-pull-request.yml`·`front-pull-request.yml`이 담당함을 문서에 명시
- [x] 3.4 액션 실패 시 (토큰 만료·rate limit) 영향 범위(머지 차단되지 않음)와 운영자 대처 절차를 문서에 명시

## 4. 자체 검증 (워크플로우 동작 확인)

- [x] 4.1 변경 사항을 feature 브랜치에 커밋하고 develop 대상 PR을 올려 `opened` 이벤트로 워크플로우가 트리거되는지 확인
- [x] 4.2 PR 페이지에서 자동 리뷰 코멘트가 정상 게시되는지 확인 — 액션 로그에서 OAuth 인증·diff 분석·코멘트 게시 단계 모두 성공인지 점검
- [x] 4.3 같은 PR에 추가 커밋을 푸시해 `synchronize` 이벤트가 워크플로우를 재트리거하고 이전 진행 중 실행이 취소되는지 (concurrency 동작) 확인
- [x] 4.4 워크플로우 실행이 실패해도 (예: secret을 일시적으로 빈 값으로 두고 테스트) PR 머지 가능 상태가 유지되는지 (non-blocking) 확인 후 secret 복구
- [x] 4.5 백엔드만 변경한 PR / 프론트엔드만 변경한 PR / 여러 디렉터리가 섞인 PR 각각에서 워크플로우가 동일하게 트리거되어 리뷰 코멘트를 남기는지 관찰 (가능하면 develop 머지 직후 수일간 모니터링)
