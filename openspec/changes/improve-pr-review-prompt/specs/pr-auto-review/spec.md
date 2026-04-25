## ADDED Requirements

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
