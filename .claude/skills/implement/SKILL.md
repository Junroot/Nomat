---
name: implement
description: |
  plan로 생성한 task 이슈를 읽고, git worktree에서 구현한 뒤 PR을 생성하는 스킬.
  이슈의 구현 내용과 변경 대상 파일을 바탕으로 코드를 작성하고, 테스트 코드도 반드시 함께 작성한다.
  "구현해줘", "#124 구현", "이 태스크 작업해줘", "코드 짜줘", "PR 만들어줘",
  "이슈 구현하고 PR 올려줘", "task 작업 시작" 등의 표현이 나올 때 트리거한다.
  plan 다음 단계로, task 라벨이 붙은 이슈를 실제 코드로 구현할 때 사용한다.
  구현과 PR 생성을 한 번에 처리하고 싶을 때 특히 유용하다.
---

# Task 구현 & PR 생성

task 이슈의 계획을 읽고, git worktree에서 격리된 환경으로 구현한 뒤 PR을 생성한다.
테스트 코드를 반드시 함께 작성한다.

## 워크플로우

### 1단계: 대상 이슈 선정

사용자가 이슈 번호를 명시하면 해당 이슈를 바로 사용한다.

```bash
gh issue view <번호> --json labels --jq '.labels[].name'
```

이슈 번호가 없으면 `task` + `verified` 라벨이 모두 붙은 열린 이슈 목록을 보여주고 선택하게 한다.

```bash
gh issue list --label "task,verified" --state open
```

선행 작업(`선행 작업: #번호`)이 아직 열려 있으면 사용자에게 알린다.

### 1-1단계: 라벨 사전 조건 확인

이슈에 `task`와 `verified` 라벨이 **모두** 있는지 확인한다.
조건을 충족하지 않으면 사용자에게 안내하고 **구현을 진행하지 않는다**.

- `task` 라벨 없음 → "이 이슈는 구현 계획이 완료되지 않았습니다. 먼저 /plan을 실행해주세요."
- `verified` 라벨 없음 → "이 이슈는 검증이 완료되지 않았습니다. 먼저 /verify를 실행해주세요."

### 2단계: 이슈 분석

이슈 본문에서 구현에 필요한 정보를 파악한다:

- **구현 내용**: 체크리스트 항목들
- **변경 대상 파일**: 어떤 파일을 수정/생성해야 하는지
- **선행 작업**: 의존하는 이슈가 완료되었는지
- **완료 기준**: PR이 충족해야 하는 조건

상위 이슈의 설계 코멘트가 있으면 함께 참고한다. 상위 이슈 번호는 이슈 본문이나 sub-issue 관계에서 확인한다.

### 3단계: 현재 브랜치 확인 및 worktree 생성

현재 브랜치를 base로 사용한다. 이슈 번호와 요약으로 브랜치를 만든다.

```bash
# 현재 브랜치 확인 (PR의 base가 됨)
BASE_BRANCH=$(git branch --show-current)

# 브랜치 이름 생성: feature/<이슈번호>-<영문요약>
# 예: feature/124-room-join-api
BRANCH_NAME="feature/<이슈번호>-<영문-kebab-case-요약>"
```

`EnterWorktree` 도구를 사용하여 worktree를 생성한다. 이렇게 하면 현재 작업 디렉토리에 영향을 주지 않고 격리된 환경에서 구현할 수 있다.

worktree에서 새 브랜치를 생성한다:

```bash
git checkout -b $BRANCH_NAME
```

### 4단계: 구현

이슈의 구현 내용을 따라 코드를 작성한다. 이 프로젝트의 아키텍처와 컨벤션을 준수한다:

**백엔드 (Kotlin/Spring Boot):**
- 헥사고날 아키텍처: in(컨트롤러) → application(서비스, DTO) → domain(엔티티, 포트) → out(저장소)
- 컨트롤러와 저장소 구현체는 `private class`
- 도메인 이벤트: `AbstractAggregateRoot` + `@TransactionalEventListener`

**프론트엔드 (React/TypeScript):**
- React Router v7 SPA, 라우트는 `app/routes.ts`
- API 호출은 `app/utils/api.ts`의 Axios 클라이언트
- Tailwind CSS v4, zinc 팔레트 + cyan-400 액센트

구현 중 이슈에 명시되지 않은 결정이 필요하면 사용자에게 질문한다.

### 5단계: 테스트 및 빌드 확인

**백엔드 변경이 포함된 경우:**
구현한 코드에 대한 테스트를 반드시 작성한다. 테스트 없이 PR을 생성하지 않는다.

테스트 작성 규칙은 `back/CLAUDE.md`의 "테스트 패턴" 섹션을 따른다. 새 도메인을 추가하면 해당 Step 클래스도 함께 생성한다.

테스트 작성 후 실행하여 통과하는지 확인한다:

```bash
cd back && ./gradlew test --tests "ilpak.nomat.모듈.테스트클래스"
```

**프론트엔드만 변경하는 경우:**
프론트엔드는 테스트 프레임워크가 없으므로 테스트 코드 작성은 건너뛴다.
타입체크(`npm run typecheck`)도 worktree에서 실행하지 않는다 — worktree에는 `node_modules`가 없어서 npm 명령이 동작하지 않기 때문이다. 타입체크는 PR의 CI에서 자동 확인된다.

### 6단계: 커밋

변경 사항을 커밋한다. 이 프로젝트는 한국어 Conventional Commits을 사용한다:

```bash
git add <변경된 파일들>
git commit -m "$(cat <<'EOF'
feat: 커밋 메시지

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

- 커밋 메시지는 한국어로 작성한다.
- 접두사: `feat:`, `fix:`, `refactor:`, `test:`, `chore:` 등
- 변경이 크면 논리적 단위로 여러 커밋으로 나눈다.

### 7단계: PR 생성

worktree에서 push하고 PR을 생성한다:

```bash
# 리모트에 push
git push -u origin $BRANCH_NAME

# PR 생성 (base는 현재 브랜치)
gh pr create --base "$BASE_BRANCH" --title "PR 제목" --body "$(cat <<'EOF'
## Summary
- 구현 내용 요약

Closes #<이슈번호>

## Test plan
- [ ] 테스트 항목

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- PR 제목은 한국어로 간결하게 작성한다.
- `Closes #<이슈번호>`로 task 이슈를 연결하여, PR 머지 시 자동으로 이슈가 닫히게 한다.
- 테스트 계획에 작성한 테스트와 수동 검증 항목을 포함한다.

### 8단계: worktree 정리 및 결과 보고

`ExitWorktree` 도구로 worktree를 정리하고 원래 작업 디렉토리로 돌아온다.

PR URL과 함께 결과를 보고한다:
- 구현 요약
- 작성한 테스트 목록
- PR URL

## 핵심 원칙

- **테스트는 필수**: 백엔드 코드를 구현하면 반드시 테스트를 함께 작성한다. 테스트가 통과하는 것을 확인한 후에만 커밋한다.
- **격리된 작업**: worktree를 사용하여 현재 작업 디렉토리에 영향 없이 구현한다.
- **이슈 기반 구현**: 이슈에 명시된 계획을 따른다. 범위를 넘어서는 변경(리팩토링, 추가 기능)은 하지 않는다.
- **한국어 컨벤션**: 커밋 메시지, PR 제목/본문은 한국어로 작성한다.
- **빌드 확인**: 백엔드 변경 시 커밋 전 테스트가 통과하는지 확인한다. 전체 빌드(`./gradlew build`)가 실패하면 수정한다. 프론트엔드만 변경하는 경우 worktree에서 npm 명령을 실행하지 않고 CI에 위임한다 (worktree에는 `node_modules`가 없음).
