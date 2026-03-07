---
name: github-issue-impl
description: |
  GitHub 이슈를 조회하고 이슈에 작성된 내용(목표, 배경, 구현 방법, 테스트 방법)을 기반으로 코드를 구현하는 스킬.
  사용자가 "이슈 구현해줘", "이슈 N번 작업해줘", "#N 구현", "이슈 해결해줘", "implement issue" 등의 표현을 사용하거나,
  특정 이슈 번호를 언급하며 구현을 요청할 때 이 스킬을 사용한다.
  기존 github-issue 스킬이 이슈 **생성**을 담당한다면, 이 스킬은 이슈 **해결(구현)**을 담당한다.
---

# GitHub Issue Implementer

GitHub 이슈를 조회하고, 이슈 본문의 구현 방법에 따라 코드를 작성하며, 테스트 안내 후 PR을 생성한다.

## 워크플로우

### 1단계: 이슈 조회

인자로 전달받은 이슈 번호로 이슈 내용을 가져온다:

```bash
gh issue view <number> --json title,body,labels
```

이슈 본문에서 다음 섹션을 파싱한다:
- **목표**: 이슈가 해결되는 기준
- **배경**: 이슈를 생성하게 된 사전 배경
- **구현 방법**: 목표를 달성하기 위한 해결 방법
- **테스트 방법**: 이슈를 해결했는지 여부를 확인하기 위한 방법

파싱한 내용을 사용자에게 요약하여 보여주고, 구현 방향에 대해 확인을 받는다.

### 2단계: 브랜치 생성

`develop` 브랜치 최신 상태에서 새 브랜치를 생성한다:

```bash
git fetch origin develop
git checkout -b feature/junroot/<이슈-키워드-kebab-case> origin/develop
```

- 브랜치명은 이슈 제목에서 핵심 키워드를 추출하여 kebab-case로 작성한다.
- 예: 이슈 제목이 "feat: 방 입장 시 WebSocket 연결 기능 구현"이면 → `feature/junroot/room-websocket`

### 3단계: 코드베이스 탐색

이슈의 구현 방법 섹션을 기반으로 관련 코드를 탐색한다:

- 프로젝트 구조 파악: `back/`, `front/`, `infra/` 중 영향 범위 확인
- 헥사고날 아키텍처 구조에서 관련 모듈 탐색 (`module/in/`, `module/out/`, `module/application/domain/`)
- 기존 패턴과 유틸리티 확인하여 일관성 있는 코드 작성 준비
- 이슈에서 언급된 기존 코드(엔티티, 서비스, 설정 등) 확인

### 4단계: 코드 구현

이슈의 구현 방법에 따라 코드를 작성한다. 다음 프로젝트 컨벤션을 준수한다:

- **헥사고날 아키텍처**: 컨트롤러는 `in/`, 저장소 구현체는 `out/`, 비즈니스 로직은 `application/`
- **접근 제어**: 컨트롤러와 저장소 구현체는 `private class`
- **언어**: UI 텍스트 및 에러 메시지는 한국어
- **기존 패턴 재사용**: 새로 작성하기보다 기존 코드 패턴을 따른다
- **최소 변경 원칙**: 이슈에서 요구하는 범위만 구현한다

### 5단계: 테스트 코드 작성

구현한 코드에 대한 테스트를 작성한다. 이슈의 **테스트 방법** 섹션을 기반으로 테스트 시나리오를 도출하되, 정상 케이스와 예외 케이스를 모두 포함한다.

#### 테스트 종류 선택 기준

구현 내용에 따라 적절한 테스트 종류를 선택한다:

- **도메인 단위 테스트**: 엔티티의 비즈니스 로직(상태 전환, 검증 등)을 구현한 경우. Spring 컨텍스트 없이 순수 JUnit으로 작성한다. 테스트 위치는 `back/src/test/kotlin/ilpak/nomat/{module}/application/domain/`
- **DTO 검증 테스트**: 새로운 Request/Response DTO를 추가한 경우. 유효성 검증 로직이 있다면 테스트한다. 테스트 위치는 `back/src/test/kotlin/ilpak/nomat/{module}/application/dto/` 또는 `back/src/test/kotlin/ilpak/nomat/{module}/dto/`
- **통합 테스트**: API 엔드포인트를 추가하거나 여러 계층이 협력하는 기능을 구현한 경우. `@IntegrationTest` 어노테이션, `WebTestClient`, Step 클래스를 사용한다. 테스트 위치는 `back/src/test/kotlin/ilpak/nomat/{module}/in/` 또는 해당 모듈의 적절한 위치

#### 테스트 작성 컨벤션

- 기존 테스트 파일의 패턴을 반드시 참고한다. 같은 모듈 내 기존 테스트가 있으면 해당 파일의 스타일을 따른다.
- 테스트 메서드명은 백틱으로 감싼 한국어 설명을 사용한다: `` `방 정원 초과 시 예외 발생` ``
- 통합 테스트에서 데이터 셋업은 Step 클래스(`PlayerStep`, `PlaylistStep`, `RoomStep` 등)와 `dummy*` 헬퍼 함수를 활용한다.
- 통합 테스트에서 인증이 필요한 요청은 `auth(playerResponse)` 확장 함수를 사용한다.
- assertion은 AssertJ (`assertThat`, `assertThatThrownBy`)를 사용한다.
- 새로운 Step 클래스나 `dummy*` 헬퍼가 필요하면 기존 Step 클래스 패턴을 참고하여 추가한다.

#### 테스트 실행 및 검증

작성한 테스트를 실행하여 통과하는지 확인한다:

```bash
./gradlew test --tests "ilpak.nomat.{module}.{TestClassName}"
```

테스트가 실패하면 원인을 분석하고 프로덕션 코드 또는 테스트 코드를 수정하여 모든 테스트가 통과할 때까지 반복한다.

### 6단계: 구현 완료 보고

구현이 완료되면 사용자에게 다음을 전달한다:

1. **변경 사항 요약**: 어떤 파일을 생성/수정했는지, 각 변경의 목적
2. **테스트 결과**: 작성한 테스트 목록과 실행 결과 (통과 여부)
3. **추가 테스트 안내**: 이슈의 테스트 방법 섹션 중 자동화하지 못한 항목(수동 UI 확인 등)이 있으면 사용자에게 안내

사용자가 결과를 확인하도록 안내하고, 피드백을 기다린다.

### 7단계: PR 생성 (사용자 확인 완료 후)

사용자가 테스트 완료를 알리면 커밋 및 PR을 생성한다:

- **커밋**: 한국어 Conventional Commits (`feat:`, `fix:` 등)
- **PR 제목**: 이슈 제목과 동일하거나 유사한 한국어 Conventional Commits 형식
- **PR 본문**: `Closes #N`으로 이슈 참조
- **base 브랜치**: `develop`

```bash
gh pr create --base develop --title "feat: 이슈 제목" --body "$(cat <<'EOF'
## Summary
- 변경 사항 요약

Closes #N

## Test plan
- 테스트 항목들

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

## 주의사항

- 이슈 본문에 4개 섹션(목표, 배경, 구현 방법, 테스트 방법)이 모두 있어야 진행한다. 누락된 섹션이 있으면 사용자에게 알린다.
- 구현 전에 반드시 이슈 내용을 사용자에게 보여주고 확인을 받는다.
- 테스트 코드는 반드시 작성하고 실행하여 통과시킨다. 사용자가 결과를 확인하고 완료를 명시적으로 알리기 전까지 PR을 생성하지 않는다.
- 구현 중 이슈의 구현 방법과 다른 접근이 필요하면 사용자에게 먼저 상의한다.