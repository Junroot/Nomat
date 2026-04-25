## Context

현재 저장소에는 PR 단위 자동화로 `back-pull-request.yml`(테스트 실행)과 `front-pull-request.yml`(빌드 + Netlify 프리뷰 배포)만 존재한다. 두 워크플로우는 머지 게이팅 역할이며, 코드 품질·잠재 버그·테스트 누락 같은 정성적 리뷰는 전적으로 사람 리뷰어에게 의존한다. 그 결과 1차 리뷰까지 대기 시간이 발생하고, 명백한 결함도 사람이 발견할 때까지 노출되지 않는다.

이번 변경은 PR이 열리거나 갱신될 때 GitHub Actions에서 Claude Code를 자동 호출해 1차 리뷰 코멘트를 남기는 신규 워크플로우를 도입한다. 머지 게이팅에는 관여하지 않으며 기존 PR 워크플로우와 독립적으로 동작한다.

제약 사항:
- 저장소가 public이라 fork PR은 secrets 접근이 불가하다 (`pull_request` 이벤트의 보안 모델).
- 인증은 Claude Pro/Max 구독 계정의 OAuth 토큰을 사용한다 — API 종량 과금이 아니라 구독 한도를 공유한다.
- OAuth 토큰은 만료가 있어 갱신 운영이 필요하다.
- 모노레포 구조라 백엔드/프론트엔드/인프라 변경이 한 PR에 섞일 수 있다.

이해관계자: 저장소 관리자(토큰 등록·갱신), PR 작성자(리뷰 코멘트 수신), 사람 리뷰어(1차 리뷰 부담 감소).

## Goals / Non-Goals

**Goals:**

- PR이 `opened` 또는 `synchronize`될 때 Claude Code가 변경 diff를 읽고 리뷰 코멘트를 자동 게시한다.
- 같은 PR에 새 커밋이 들어오면 진행 중인 리뷰는 취소하고 최신 커밋만 리뷰한다 (사용량·노이즈 절감).
- 리뷰는 non-blocking으로 동작해 false positive로 머지를 막지 않는다.
- 백엔드·프론트엔드·인프라 어떤 디렉터리 변경이든 단일 워크플로우로 일관된 리뷰를 제공한다.
- OAuth 토큰 발급·등록·갱신 절차가 README 또는 별도 문서로 추적 가능하다.

**Non-Goals:**

- 사람 리뷰어를 대체하지 않는다 — 1차 자동 리뷰일 뿐 머지 승인은 사람이 한다.
- 기존 `back-pull-request.yml`, `front-pull-request.yml`을 통합·수정하지 않는다 (독립 워크플로우 추가).
- fork PR에 대한 자동 리뷰는 지원 범위 외다 (`pull_request_target` 사용으로 인한 보안 위험을 수용하지 않는다).
- 코드 자동 수정·자동 커밋·자동 머지는 하지 않는다 (코멘트만 남긴다).
- API 키 종량제 인증·다른 LLM 제공자 통합은 다루지 않는다.
- 리뷰 대상 파일 화이트리스트/블랙리스트(예: 문서 PR 제외) 같은 세밀한 필터링은 도입하지 않는다 (모든 PR을 동일하게 리뷰).

## Decisions

### Decision 1: 액션은 `anthropics/claude-code-action` 공식 액션을 사용한다

**선택**: 공식 마켓플레이스 액션 `anthropics/claude-code-action`을 호출한다.

**근거**:

- Anthropic이 직접 유지보수하므로 Claude Code 인증 모델·기능 변화에 가장 빠르게 대응된다.
- OAuth 토큰 입력, PR 컨텍스트 수집, 코멘트 게시까지 액션 안에 캡슐화되어 우리 쪽 워크플로우 YAML이 단순해진다.
- `pull-requests: write` 권한과 `CLAUDE_CODE_OAUTH_TOKEN` secret만 주면 동작 — 우리 저장소에 사용자 정의 스크립트를 두지 않아도 된다.

**대안 고려**:

- *직접 `gh` CLI + `claude` CLI 호출 스크립트 작성*: 자유도는 높지만 OAuth flow·diff 추출·코멘트 마크다운 포맷팅·rate limit 처리까지 직접 다뤄야 해 유지보수 부담이 크다. 도입 단계에서는 공식 액션이 합리적이며, 한계가 발견되면 그때 대체 검토.
- *서드파티 PR 리뷰 봇(CodeRabbit 등)*: 이번 변경의 전제(Claude Pro/Max 구독 활용)와 다르고, 별도 계약·과금 모델이 필요하다.

### Decision 2: 인증은 OAuth 토큰(`CLAUDE_CODE_OAUTH_TOKEN`)으로 한다

**선택**: Anthropic API 키 대신 `claude setup-token`으로 발급한 OAuth 토큰을 GitHub Secrets에 저장하고 액션에 주입한다.

**근거**:

- 이미 보유한 Claude Pro/Max 구독 한도 안에서 사용 가능 — PR 트래픽 단위로 별도 종량제 비용이 발생하지 않는다.
- API 키와 달리 사용자 계정에 묶여 있어 권한 회수·갱신이 명시적이다.

**대안 고려**:

- *Anthropic API 키*: 종량제 과금이 발생하며 PR 폭주 시 비용 예측이 어렵다. 구독을 이미 보유한 상태에서 비용 효율이 떨어진다.
- *GitHub App 자체 발급 토큰*: 별도 GitHub App 운영 부담이 있고, Claude 인증과는 별개라 결국 OAuth 토큰이 필요하다.

**트레이드오프**:

- OAuth 토큰은 만료가 있어 갱신 누락 시 워크플로우가 조용히 실패한다 → Risks 섹션에서 대응 명시.
- 발급한 사용자 계정의 구독 한도를 공유하므로, 동일 계정으로 IDE/터미널에서 Claude를 많이 쓰면 PR 리뷰가 rate limit에 걸릴 수 있다.

### Decision 3: 트리거는 `pull_request` 이벤트만 사용한다 (`pull_request_target` 미사용)

**선택**: `on: pull_request` + `types: [opened, synchronize]`로 트리거한다. `pull_request_target`은 사용하지 않는다.

**근거**:

- `pull_request_target`은 base 저장소 컨텍스트에서 실행되어 fork PR에서도 secrets에 접근할 수 있지만, 동시에 PR 코드를 신뢰된 컨텍스트에서 체크아웃하는 순간 임의 코드 실행으로 secrets가 유출될 수 있다 (well-known supply-chain 취약 패턴).
- 우리 저장소는 public이므로 이 위험을 수용할 수 없다.
- `pull_request` 사용으로 fork PR 자동 리뷰는 포기하지만, fork PR은 빈도가 낮고 maintainer 수동 리뷰가 안전한 대안이다.

**대안 고려**:

- *`pull_request_target` 사용 + checkout 제한*: 코드 체크아웃 없이 메타데이터만 활용하는 방식도 가능하나, 리뷰 액션은 본문 diff/파일 내용에 접근해야 하므로 결국 위험이 재발생한다.

### Decision 4: 동시성 제어는 PR 단위 `concurrency` 그룹 + `cancel-in-progress: true`

**선택**:

```yaml
concurrency:
  group: claude-pr-review-${{ github.event.pull_request.number }}
  cancel-in-progress: true
```

**근거**:

- 같은 PR에 푸시가 연달아 들어오면 이전 커밋 기준 리뷰는 무의미해진다 — 진행 중 작업을 취소해 구독 한도와 리뷰 노이즈를 함께 절약한다.
- 다른 PR끼리는 그룹 키가 달라 병렬 리뷰가 가능하다 (한 PR이 다른 PR을 막지 않음).

**대안 고려**:

- *`cancel-in-progress: false`*: 모든 커밋을 리뷰하지만 의미 없는 중간 커밋까지 토큰을 소모한다.
- *전역 단일 그룹*: 저장소 전체에서 한 번에 한 PR만 리뷰 → 처리량이 떨어지고 PR 작성자 대기 시간이 늘어난다.

### Decision 5: 워크플로우는 모든 경로 변경에 트리거한다 (paths 필터 없음)

**선택**: `paths` 필터를 두지 않고 PR의 `opened`/`synchronize` 모든 이벤트에 동작한다.

**근거**:

- 기존 `back-pull-request.yml`/`front-pull-request.yml`은 빌드·테스트 비용이 커 paths로 제한했지만, 이번 워크플로우는 LLM 호출 자체가 가벼운 단일 잡이며 변경 diff에 따라 비용이 자동 조절된다.
- 모노레포라 한 PR이 back/front/infra를 동시 변경할 수 있고, 단일 워크플로우가 cross-cutting 컨텍스트를 함께 보는 편이 리뷰 품질에 유리하다.
- 분기 로직을 두면 paths 매칭 누락으로 일부 PR이 조용히 리뷰에서 빠질 수 있어 운영 위험이 늘어난다.

**대안 고려**:

- *paths 필터로 코드 디렉터리만 트리거*: 문서 전용 PR을 제외할 수 있으나 절약 효과가 작고 누락 위험이 더 크다.
- *PR 라벨 기반 트리거*: 작성자가 `review` 라벨을 붙여야 동작 → 자동화 취지에 어긋난다.

### Decision 6: 권한은 최소한으로 부여 — `pull-requests: write`, `contents: read`

**선택**: 워크플로우 또는 잡 레벨 `permissions:` 블록에 두 권한만 명시한다. 그 외(`issues`, `id-token`, `actions` 등)는 부여하지 않는다.

**근거**:

- 코멘트 게시: `pull-requests: write` 필요.
- diff·소스 읽기: `contents: read` 필요.
- 그 외 권한은 자동 리뷰 범위에서 불필요하며, 토큰 유출 시 영향을 줄이는 보안 기본 원칙(least privilege)을 따른다.

### Decision 7: 토큰 만료·발급 절차는 README 또는 운영 문서에 명시한다

**선택**: 변경 작업의 일부로 토큰 발급 절차(`claude setup-token` 실행, GitHub Secrets `CLAUDE_CODE_OAUTH_TOKEN` 등록, 만료 시 갱신 방법)를 저장소 문서(README 또는 `docs/operations.md` 신규)에 추가한다.

**근거**:

- 토큰은 저장소 관리자만 발급/등록할 수 있어 절차가 문서화되지 않으면 만료 시 다른 협업자가 복구하지 못한다.
- 만료된 토큰으로 액션이 실패해도 PR 머지는 막히지 않으므로(non-blocking) 장애가 조용히 누적될 수 있다 — 문서가 있어야 운영자가 빠르게 대처한다.

## Risks / Trade-offs

- **OAuth 토큰 만료로 워크플로우가 조용히 실패한다** → Mitigation: (1) 토큰 만료/갱신 절차를 README에 명시, (2) 액션 실패가 일정 기간 누적되면 GitHub의 워크플로우 실패 알림 또는 별도 모니터링으로 감지(추후 고도화 항목으로 분리).
- **구독 한도(rate limit) 초과로 일부 PR 리뷰가 누락될 수 있다** → Mitigation: (1) 동시성 제어로 같은 PR 중복 호출 차단, (2) 한도 초과는 일시적이며 PR 머지를 막지 않으므로 작업 흐름에 치명적이지 않음. 추후 사용량이 문제 수준이면 리뷰 대상 PR 필터(예: `[skip-review]` 라벨) 도입 검토.
- **fork PR은 자동 리뷰가 동작하지 않는다** → Mitigation: 의도된 보안 트레이드오프임을 README에 명시하고 maintainer 수동 리뷰를 안내.
- **Claude가 false positive 코멘트를 남겨 PR 작성자가 대응하느라 시간을 쓸 수 있다** → Mitigation: (1) 리뷰 결과를 non-blocking 코멘트로 게시(머지 차단 안 함), (2) 작성자가 코멘트를 무시할 수 있는 사회적 합의 명시. 운영 후 코멘트 품질을 관찰해 액션 입력(프롬프트, 모델 등)을 조정.
- **공식 액션의 입력 스키마가 변경되면 워크플로우가 깨질 수 있다** → Mitigation: 액션 버전을 SHA 또는 메이저 태그로 핀 고정, Dependabot 또는 수동 점검으로 업데이트 추적.
- **백엔드(Kotlin/Spring) 컨텍스트가 큰 PR에서 토큰 사용량이 급증할 수 있다** → Mitigation: 액션이 제공하는 변경 파일 한정 옵션을 사용하고(전체 저장소 인덱싱 회피), 필요 시 모델/프롬프트 튜닝으로 비용 조절.

## Migration Plan

1. **사전 준비 (저장소 관리자 1회 작업)**:
   - 로컬에서 `claude setup-token` 실행 → OAuth 토큰 발급.
   - GitHub 저장소 Settings → Secrets and variables → Actions → `CLAUDE_CODE_OAUTH_TOKEN` 신규 등록.
2. **워크플로우 추가**:
   - `.github/workflows/claude-pr-review.yml` 신규 작성 (Decisions 1–6 반영).
   - feature 브랜치에서 PR로 올려 자체 리뷰가 동작하는지 확인.
3. **문서화**:
   - README 또는 `docs/operations.md`에 토큰 발급·갱신·실패 시 대처 절차 추가.
4. **머지·관찰**:
   - `develop`에 머지 후 첫 N개 PR에서 리뷰 코멘트 품질·실행 시간·rate limit 발생 여부를 관찰.
   - 문제가 있으면 모델·프롬프트·트리거 조건을 후속 변경으로 조정.

**롤백 전략**:

- 워크플로우는 독립 파일이며 머지 게이팅이 아니므로 문제가 발생하면 `.github/workflows/claude-pr-review.yml` 삭제 PR 한 건으로 즉시 롤백 가능.
- 토큰은 그대로 두어도 무해하지만, 영구 폐기 시 `claude` CLI에서 토큰 회수 + GitHub Secret 삭제로 마무리.
- 인프라(DB/ES/Kafka/Redis)나 애플리케이션 코드 변경이 없어 데이터 마이그레이션 롤백은 불필요.

## Open Questions

- 액션 호출 시 사용할 모델·프롬프트의 기본값을 워크플로우 YAML에 고정할지, 별도 설정 파일(예: `.github/claude-review-config.yml`)로 분리할지 — 운영 후 튜닝 빈도를 보고 결정.
- 리뷰 결과를 PR 코멘트로만 둘지, GitHub Checks의 informational check로도 노출해 PR UI에서 더 잘 보이게 할지 — 액션이 지원하는 출력 모드를 확인 후 결정.
- 토큰 만료 사전 알림 자동화(예: 매월 워크플로우가 토큰 유효성을 점검해 실패 시 issue 자동 생성)를 이번 변경 범위에 포함할지, 후속 변경으로 분리할지.
- 리뷰 비활성화 옵션(예: PR 본문에 `[skip-review]` 또는 라벨)을 처음부터 도입할지, 사용량을 본 뒤 결정할지.
