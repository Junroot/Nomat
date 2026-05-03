## Context

`add-claude-pr-review-action` 변경으로 도입된 `.github/workflows/claude-pr-review.yml`의 `prompt` 블록은 5개 항목(버그·테스트·보안·코드 품질·일관성)을 단순 나열한 1차 버전이다. 운영을 막 시작한 단계라 코멘트 패턴이 굳기 전이지만, 다음과 같은 공백이 있다:

- **심각도 분류 부재**: 모든 지적이 평문 코멘트로 나가 PR 작성자가 "꼭 고쳐야 할 것"과 "취향 차이"를 구분하기 어렵다.
- **High-signal 필터 부재**: Detekt(`./gradlew detekt`)·TypeScript 컴파일러·Flyway 검증이 이미 잡는 항목을 LLM이 중복 지적할 가능성이 높다.
- **증거 인용 미강제**: `file:line` 인용 없이 추측성 결함을 지적하면 false positive가 늘고 작성자 검증 비용이 커진다.
- **노이즈 상한 없음**: nit성 코멘트가 무제한 누적되면 PR 1건에 수십 개 코멘트가 붙어 자동 리뷰의 신뢰가 떨어진다.
- **결과 없음 포맷 미정의**: 결함이 없는 PR에서도 길고 모호한 "특별한 문제 없음" 코멘트가 게시되어 시그널-노이즈 비를 악화시킨다.
- **프로젝트 컨벤션 부재**: 헥사고날 패키지 배치·`private class`·`AbstractAggregateRoot`+`@TransactionalEventListener(AFTER_COMMIT)`·React Router v7·Zustand·Tailwind zinc 컨벤션이 프롬프트에 명시되지 않아 일관성 위반을 잡을 근거가 약하다.

업계 LLM 코드 리뷰 best practice(Anthropic 공식 Code Review 플러그인, GitHub PR 자동 리뷰 운영 사례)는 **심각도 라벨 + 증거 인용 + nit cap + 정적 분석 중복 회피** 4가지를 노이즈 억제의 핵심으로 꼽는다. 운영 직후가 변경 비용이 가장 낮은 시점이므로 이 best practice를 프롬프트에 한 번에 반영한다.

이해관계자: PR 작성자(코멘트 노이즈 감소·심각도 직관 향상), 사람 리뷰어(자동 코멘트의 신뢰도 상승으로 1차 검토 부담 감소), 저장소 관리자(spec으로 회귀 방지).

제약:
- 단일 워크플로우 YAML에 프롬프트가 인라인되어 있다 — 외부 파일로 분리하지 않는다(Non-Goals).
- 액션 버전·인증·트리거·동시성·권한은 `add-claude-pr-review-action`에서 결정된 그대로 유지한다.
- `pr-auto-review` 스펙은 아직 `openspec/specs/`에 동기화되지 않았고 `add-claude-pr-review-action`의 delta로만 존재한다 — 본 변경의 delta는 후행 정렬이 필요하다(Migration Plan 참고).

## Goals / Non-Goals

**Goals:**

- 자동 리뷰 가이드라인을 단일 마크다운 파일(`REVIEW.md`)로 분리해 워크플로우 YAML이 다단계 multi-line 문자열로 비대해지는 것을 방지한다.
- 가이드라인이 Persona·심각도 라벨·high-signal 필터·증거 인용·nit cap·결과 없음 포맷·출력 형식·컨벤션 컨텍스트·🔴/🟡 경계 예시를 모두 포함하도록 작성한다.
- Detekt/typecheck/Flyway가 이미 잡는 항목과 입력 의존적 가설성 결함을 명시적으로 코멘트 금지 목록에 둔다.
- 결함 지적 시 `file:line` 인용을 의무화해 인라인 코멘트의 정확도를 끌어올린다.
- `pr-auto-review` 스펙에 **자동 리뷰 프롬프트 품질 요구사항**을 신규 Requirement로 추가해 향후 프롬프트 회귀를 spec 차원에서 차단한다.
- 가이드라인이 헥사고날 모듈(`playlist`, `room`, `player`, `favoriteplaylist`, `auth`)·`in/out/application` 계층·`private class` 컨벤션·React Router v7·Zustand·Tailwind zinc 등 핵심 컨벤션을 인지하도록 컨텍스트를 본문에 주입한다.
- 🔴 vs 🟡 경계, 특히 "테스트 누락" 케이스 분류를 기존 테스트 코드 인용으로 구체화해 LLM의 라벨 일관성을 끌어올린다.

**Non-Goals:**

- 외부 LLM 평가 인프라(별도 평가 스크립트로 프롬프트 회귀 자동 테스트)는 도입하지 않는다 — 회귀 방지는 spec Requirement로 충분.
- 모델 교체·`anthropics/claude-code-action` 버전 업그레이드는 다루지 않는다.
- fork PR 자동 리뷰, 머지 게이팅 전환, Anthropic Code Review 관리형 서비스 마이그레이션은 다루지 않는다.

## Decisions

### Decision 1: 심각도 라벨로 🔴 Important / 🟡 Nit / 🟣 Pre-existing 3단계를 채택한다

**선택**: 모든 결함 코멘트는 본문 첫 줄에 정확히 한 개의 라벨을 붙인다.

- 🔴 Important — 머지 전에 반드시 다뤄야 하는 결함(버그·보안·데이터 손실 가능성·테스트 누락 중 머지 후 회귀 시 운영 영향이 큰 것).
- 🟡 Nit — 권장 사항(가독성·네이밍·미세한 중복). 무시 가능.
- 🟣 Pre-existing — 이번 PR이 도입하지 않았지만 diff 컨텍스트에서 보이는 기존 결함. 작성자가 책임지지 않으며 별도 이슈/후속 PR 권장.

**🔴 vs 🟡 경계 — "테스트 누락" 케이스 예시**

라벨 분류에서 가장 모호한 영역이 "테스트가 빠졌다"이므로, 기존 테스트 코드의 패턴을 인용해 경계를 구체화한다. 이 예시는 `REVIEW.md`에도 그대로 옮긴다.

🔴 Important로 매겨야 하는 "테스트 누락" — 회귀 시 운영 데이터·세션·인증·정합성에 직접 타격:

- **동시성·경합 조건이 신규 도입됐는데 동시 호출 테스트가 없는 경우**. 참고 패턴: `back/src/test/kotlin/ilpak/nomat/room/application/in/RoomJoinIntegrationTest.kt:115-147` — `CountDownLatch`+스레드 풀로 정원 초과 join 차단을 검증. 새 동시성 분기가 같은 형태의 테스트 없이 들어오면 🔴.
- **세션 교체·재연결 grace period 분기가 신규/변경됐는데 시나리오 테스트가 없는 경우**. 참고 패턴: `RoomSessionReplaceIntegrationTest.kt:54-79`(SESSION_REPLACED 이벤트 브로드캐스트), `RoomLeaveIntegrationTest.kt:54-80`(grace period 안 재연결 시 leave 미발행).
- **인증·인가 분기 추가**. 참고 패턴: `RoomTest.kt:120-154`(비밀번호 불일치 → `ForbiddenException`), `RoomJoinIntegrationTest.kt:74-80`(잘못된 비밀번호 join 거부).
- **트랜잭셔널 이벤트 / Redis Pub/Sub / Kafka·Debezium CDC 흐름 변경**. 발행 실패가 ES 인덱스·다른 구독자와의 정합을 깬다 — `AbstractAggregateRoot`+`@TransactionalEventListener(AFTER_COMMIT)` 패턴이 도입되거나 기존 발행 채널이 바뀌면 🔴.
- **Flyway 마이그레이션 무결성**(NULL 제약 추가, 인덱스 변경, 백필 로직). 데이터 손실/롤백 가능성과 직결되므로 🔴.

🟡 Nit으로 매겨야 하는 "테스트 누락" — 회귀해도 입력 검증/응답 형식 정도에 그침:

- **DTO Bean Validation 추가 boundary 케이스**. 같은 어노테이션 그룹에 이미 min/max 테스트가 있는 상태에서 중간 값 변형만 누락된 경우. 참고 패턴: `back/src/test/kotlin/ilpak/nomat/player/dto/PlayerNicknameRequestTest.kt:12-35`.
- **단순 DTO 필드 매핑 테스트**. 게터 호출 결과 비교만 하는 트리비얼 케이스. 참고 패턴: `back/src/test/kotlin/ilpak/nomat/room/application/dto/RoomDetailResponseTest.kt`.
- **이미 커버된 happy path의 추가 입력 변형**. 같은 컨트롤러 엔드포인트의 단일 happy-path 테스트가 있고, 작성자가 추가한 변형이 동작 분기 자체를 바꾸지 않는 경우. 참고 패턴: `back/src/test/kotlin/ilpak/nomat/player/in/PlayerControllerTest.kt:37-44`.

판정 원칙: **"이 테스트가 회귀를 막지 못했을 때 운영 데이터·사용자 세션·결제·인증·정합성에 영향이 가는가?"** — 예 → 🔴, 아니오 → 🟡. 분류가 모호하면 보수적으로 🟡로 매기고 본문에서 그 이유를 1줄 명시한다(예: "동시성 분기지만 단일 사용자 시나리오라 회귀 영향이 제한적 → 🟡").

**근거**:

- Anthropic 공식 Code Review 플러그인이 동일한 3단계 라벨을 사용하며, 외부 협업자가 PR 코멘트의 우선순위를 즉시 파악할 수 있는 검증된 패턴이다.
- 라벨이 "꼭 고쳐야 할 것"과 "취향"을 시각적으로 구분해 PR 작성자가 어떤 코멘트에 답해야 하는지 자체 판단할 수 있다.
- 🟣 Pre-existing 라벨이 있으면 LLM이 PR과 무관한 기존 결함을 발견했을 때 작성자에게 부담을 떠넘기지 않으면서도 정보는 보존할 수 있다.

**대안 고려**:

- *2단계(must-fix / suggestion)*: 단순하지만 PR과 무관한 기존 결함을 표현할 자리가 없어 모두 must-fix 또는 suggestion으로 가게 된다 — 작성자 부담이 늘거나 정보가 사라진다.
- *severity 점수(P0/P1/P2)*: 정량성이 있지만 LLM이 일관된 점수를 매기기 어렵고 한국어 운영 컨텍스트에서 직관성이 떨어진다.

### Decision 2: High-signal 필터 — 정적 분석이 잡는 항목과 가설성 결함은 코멘트 금지

**선택**: 프롬프트에 명시적인 **코멘트 금지 목록**을 둔다.

- Detekt 규칙으로 이미 검출되는 Kotlin 스타일/복잡도 위반.
- TypeScript 컴파일러(`npm run typecheck`)가 잡는 타입 오류.
- Flyway 마이그레이션 무결성 검증이 잡는 SQL 문법/순서 문제.
- "이 입력이 들어오면 깨질 수 있다" 식의 입력 의존적 가설성 결함(실제 호출 경로 증거 없이 가능성만 제기).
- 단순 스타일·공백·임포트 순서 같은 마이크로 nitpick.
- 명시적 요청 없이 시도하는 구조적 리팩터링 제안(코드 품질이 명백히 무너진 경우만 🟡로 한정).

**근거**:

- 정적 분석 도구는 빠르고 결정적이다 — 같은 항목을 LLM이 중복 지적하면 토큰만 쓰고 새로 얻는 정보가 없다.
- 우리 CI(`back-pull-request.yml`)가 Detekt를 reviewdog로 PR 코멘트로 게시하므로 같은 채널에서 라인을 두 번 채울 위험이 크다.
- "가능성만 제기" 코멘트는 작성자가 검증해도 결론이 안 나와 시간만 소비한다 — 증거 인용 의무(Decision 3)와 결합해 차단한다.

**대안 고려**:

- *프롬프트는 그대로 두고 운영 후 reviewdog 출력과 LLM 출력을 후처리로 dedup*: 후처리 파이프라인 추가 비용이 크고, 프롬프트가 처음부터 중복 영역을 알면 토큰 자체를 절약할 수 있다.
- *코멘트 금지 대신 "낮은 우선순위로 다루라"*: LLM은 "가능하면"과 "절대로"를 구분 못 하는 경우가 잦아 명시적 금지가 강제력이 높다.

### Decision 3: 결함 지적은 `file:line` 인용을 의무화한다

**선택**: 모든 🔴·🟡 코멘트는 PR diff 안의 구체적 `path/to/file:line` 위치를 본문에 인용해야 한다. 인용할 위치가 없으면 코멘트를 만들지 않는다.

**근거**:

- 위치가 명시되면 작성자가 1초 안에 검증할 수 있어 false positive의 검증 비용이 줄어든다.
- LLM이 위치를 못 짚으면 대개 환각(diff에 없는 코드 추측)이므로, 인용 강제 자체가 환각 필터로 작동한다.
- 인라인 코멘트(`mcp__github_inline_comment__create_inline_comment`) 사용 시 자연스럽게 라인 좌표가 필요해 도구 호출 형태와도 일치한다.

**대안 고려**:

- *인용은 권장만 하고 강제하지 않음*: 운영 초기 LLM이 "어딘가에서 X가 있을 수 있다" 식 코멘트를 생성하기 쉬운데, 강제 없이 줄이기 어렵다.

### Decision 4: 한 리뷰당 🟡 Nit 코멘트는 최대 5개로 제한한다

**선택**: 🟡 라벨 코멘트가 5개를 넘어가면 추가분은 인라인 코멘트로 만들지 않고 상위 요약 코멘트의 카운트(예: "🟡 Nit 8건 중 상위 5건만 인라인으로, 나머지 3건은 생략")로만 표기한다. 🔴는 상한 없음, 🟣는 PR 본문 내 등장 빈도 기준 자연스러운 수만큼 허용한다.

**근거**:

- nit성 코멘트가 무제한이면 작성자가 중요한 🔴를 놓치고, PR 페이지 자체가 자동 코멘트로 도배된다.
- 5건 상한은 Anthropic Code Review 플러그인이 사용하는 값과 같고, 일반적인 PR 규모에서 작성자가 한 번에 소화 가능한 임계로 알려져 있다.
- 🔴는 머지 전 처리해야 하므로 상한을 두면 진짜 중요한 결함이 잘릴 위험이 있다 — nit만 제한한다.

**대안 고려**:

- *전체 코멘트 수에 cap*: 🔴를 자르면 신뢰도가 무너진다.
- *PR 크기에 비례한 동적 cap*: 구현이 복잡하고 LLM이 일관되게 따르기 어렵다.

### Decision 5: 결함 미발견 PR에는 짧은 단일 확인 코멘트만 남긴다

**선택**: 결함을 못 찾으면 상위 코멘트 1건만 남기고 인라인은 만들지 않는다. 본문은 다음 형식으로 고정:

```
✅ 자동 리뷰 결과 머지 차단 수준의 결함을 발견하지 못했습니다. 사람 리뷰어의 최종 검토를 권장합니다.
```

**근거**:

- 결함이 없을 때 "전반적으로 잘 작성됐습니다", "일관성이 좋습니다" 같은 칭찬 코멘트는 정보 가치가 없고 PR 페이지 노이즈만 키운다.
- 짧고 일정한 형식이면 PR 작성자가 "자동 리뷰가 끝났는지" 즉시 알 수 있다.
- "사람 리뷰어 최종 검토 권장" 문구로 자동 리뷰가 머지 승인이 아니라는 점을 매번 상기시켜 사회적 합의를 강화한다.

**대안 고려**:

- *결함이 없으면 코멘트 자체를 생략*: GitHub Actions 실행 결과만 success로 끝나면 PR 페이지에서 자동 리뷰가 동작했는지 확인이 어렵다 — 워크플로우 실패와 "결함 없음"이 시각적으로 구분이 안 된다.

### Decision 6: 인라인 vs 상위 코멘트 출력 규칙을 명시한다

**선택**:

- **인라인 코멘트**: 라인 단위 결함 지적(🔴·🟡·🟣). 반드시 라벨 + `file:line` + 결함 설명 + (선택) 1~3줄 권장 패치를 포함한다.
- **상위 코멘트**: PR 전반의 요약(라벨별 카운트, nit cap 초과 시 잘림 표기, 🟣 Pre-existing 일괄 안내). 결함 미발견 시 Decision 5 형식 단일 코멘트.

**근거**:

- 라인 단위 결함은 GitHub PR UI의 인라인 코멘트가 가장 발견성이 높다.
- 전체 통계(카운트, 잘림)는 인라인에 흩어두면 보이지 않는다 — 상위 코멘트 1개로 묶어야 작성자가 "총 몇 건인지" 한눈에 본다.
- 두 채널을 명확히 분리하면 LLM이 같은 결함을 두 곳에 중복 게시하는 패턴을 줄인다.

**대안 고려**:

- *상위 코멘트만 사용*: 라인 추적이 안 돼 작성자가 매번 본문에서 위치를 다시 찾는다.
- *인라인 코멘트만 사용*: 전체 통계와 결과 없음 알림을 표현할 자리가 없다.

### Decision 7: 자동 리뷰 가이드라인은 저장소 루트의 `REVIEW.md`로 분리하고 워크플로우는 이를 참조한다

**선택**: 저장소 루트에 `REVIEW.md`를 신규 추가해 자동 리뷰 가이드라인 본문(페르소나·심각도 라벨·🔴/🟡 경계 예시·high-signal 필터·증거 인용 의무·nit cap·결과 없음 포맷·출력 형식·프로젝트 컨벤션)을 마크다운으로 작성한다. 워크플로우의 `prompt:` 블록은 짧게 유지해 PR 컨텍스트(`REPO`, `PR NUMBER`)와 다음 명령형 지시문만 둔다: "**먼저 저장소 루트의 `REVIEW.md`를 읽고 그 가이드 전체를 따라 이 PR을 리뷰하라.**"

**근거**:

- Decisions 1–6 + 컨벤션 컨텍스트 + 🔴/🟡 경계 예시까지 합치면 가이드라인이 100줄을 넘어선다. YAML multi-line string으로 인라인하면 이스케이핑·들여쓰기 관리가 까다롭고 코드 리뷰에서 가독성이 떨어진다.
- 마크다운 파일은 GitHub에서 미리보기·문법 하이라이트·앵커 링크가 동작해 가이드라인 자체에 대한 PR 리뷰 비용이 낮다 — 가이드는 운영하면서 자주 다듬을 텍스트라 이 비용을 줄이는 것이 중요하다.
- `actions/checkout@v4`로 이미 저장소가 체크아웃되어 있어 액션 내부 에이전트의 `Read` 도구로 즉시 접근 가능하다 — 추가 fetch 단계가 필요 없다.
- spec Requirement에서 "`REVIEW.md`가 존재하고 다음 항목을 포함해야 한다"로 회귀 방지 기준점이 한 파일로 명확해진다.
- `REVIEW.md`라는 파일명·위치는 GitHub PR 트리에서 즉시 눈에 띄어 새 협업자의 진입 비용이 낮다.

**대안 고려**:

- *YAML 인라인 유지*: 분량이 작을 때(현재 5개 항목 단순 나열)는 단순했지만 컨벤션 + 🔴/🟡 경계 예시까지 담으면 단점이 더 크다. 운영 후 분량을 줄이게 되면 다시 인라인으로 합치는 것은 1줄 PR로 가능하므로 가역적이다.
- *`.github/CLAUDE_REVIEW.md`로 두기*: 자동화 설정과의 위치 일관성은 있지만, 저장소 루트가 발견성에서 우월하고 GitHub의 README 옆 `REVIEW.md` 컨벤션과도 친숙하다.
- *`docs/pr-review-prompt.md`로 두기*: 위치 의미는 가장 명시적이지만 자동화 설정 성격을 `docs/`에 두면 프로젝트 문서와 운영 설정이 섞여 관리 동선이 길어진다.
- *`anthropics/claude-code-action`의 `prompt-file:` 같은 입력 사용*: 액션이 해당 입력을 정식 지원하는지 확인 필요. 지원해도 본 결정은 에이전트 `Read` 호출 방식과 양립하므로(워크플로우 prompt에서 명령형 지시문만 두면 됨), 액션 입력 의존도를 낮춰 둔다.

**트레이드오프**:

- 워크플로우 YAML 외에 새 파일이 하나 더 생겨 "프롬프트는 어디 있나?"를 처음 보는 사람이 한 단계 더 따라가야 한다 → 워크플로우의 `prompt:` 블록 첫 줄을 "먼저 `REVIEW.md`를 읽어라"로 명시해 1초 안에 추적되도록 한다.
- 에이전트가 `Read`를 호출하지 않고 자체 추측으로 동작할 위험 → Risks 섹션 + 자체 PR 검증 단계에서 라벨/인용/cap 누락이 즉시 드러나도록 설계.

### Decision 8: 프로젝트 컨벤션은 `REVIEW.md` 본문에 직접 명시한다

**선택**: `REVIEW.md`에 다음 핵심 컨벤션을 컨텍스트 섹션으로 명시한다(요약·키워드 위주, `back/CLAUDE.md`·`front/CLAUDE.md` 전체 복사 금지).

- **백엔드**: 헥사고날 아키텍처, 도메인 모듈 목록(`playlist`, `room`, `player`, `favoriteplaylist`, `auth`), 모듈 내부 구조(`in/`, `out/`, `application/{domain,dto,*Service.kt}`), 컨트롤러·저장소 구현체 `private class` 강제, 도메인 이벤트는 `AbstractAggregateRoot` + `@TransactionalEventListener(AFTER_COMMIT)`, 횡단 관심사는 `infrastructure/` 패키지.
- **프론트엔드**: React Router v7 SPA, 라우트 수동 정의(`app/routes.ts`), `~/` = `./app/` 별칭, SVG는 `?react` 임포트, API 호출은 `app/utils/api.ts` Axios 클라이언트, Zustand 상태 관리, Tailwind zinc 팔레트 + cyan-400 액센트 다크 테마.
- **공통**: 한국어 Conventional Commits, UI 텍스트·에러 메시지 한국어, 메인 브랜치 `develop`.

**근거**:

- 컨벤션을 모르면 LLM이 "관행과 다른 패턴"을 결함으로 못 짚는다 — 일관성 검증의 본질적 입력이다.
- `REVIEW.md` 본문에 직접 적으면 매 PR마다 LLM이 같은 컨텍스트를 보게 되어 컨벤션 적용이 결정적으로 동작한다 — 여러 외부 파일을 따로따로 읽게 두면 일부만 적용되거나 누락될 위험이 있다.
- `REVIEW.md`는 자동 리뷰 운영 단일 소스이므로, 컨벤션이 바뀌면 `REVIEW.md` 한 파일만 PR로 갱신하면 된다 — 동기화 책임이 한 곳에 모인다.

**대안 고려**:

- *`REVIEW.md`에서 `back/CLAUDE.md`·`front/CLAUDE.md`를 추가로 읽으라고 지시*: 도구 호출이 매 PR 2~3회 더 발생해 토큰을 더 쓰고, 두 CLAUDE.md가 컨벤션 외 광범위한 가이드를 담고 있어 노이즈도 늘어난다. `REVIEW.md`에 핵심만 발췌하는 편이 낫다.
- *컨벤션 섹션을 별도 파일(`REVIEW.conventions.md`)로 더 분리*: 한 PR 리뷰에 두 파일을 모두 읽어야 하고, 분리 이득(편집 빈도 차이)이 명확하지 않다.

### Decision 9: `claude_args.--allowedTools`에 `Bash(gh search:*)`, `Bash(gh issue list:*)`를 추가한다

**선택**: 현재 허용 도구 목록(`mcp__github_inline_comment__create_inline_comment`, `Bash(gh pr comment:*)`, `Bash(gh pr diff:*)`, `Bash(gh pr view:*)`)에 `Bash(gh search:*)`와 `Bash(gh issue list:*)`를 추가한다.

**근거**:

- 🟣 Pre-existing 라벨을 정확히 붙이려면 "이 결함이 기존 코드에서 비롯된 건지"를 확인할 수 있어야 한다 — `gh search`로 관련 이슈/PR을, `gh issue list`로 알려진 이슈를 조회할 수 있다.
- 변경 컨텍스트가 모호한 PR(예: 인프라+백엔드 동시 변경)에서 LLM이 관련 이슈를 찾아 의사결정 근거로 삼을 수 있다.
- 두 명령은 모두 read-only이며, 자동 코멘트 게시 외 부수효과가 없다 — 권한 확장 위험이 작다.

**대안 고려**:

- *추가하지 않음*: Pre-existing 판정이 추측에 의존하게 되어 라벨 신뢰도가 떨어진다.
- *`Bash(gh:*)`로 일괄 허용*: 머지 트리거·라벨 변경·이슈 생성 같은 쓰기 작업까지 열려 최소 권한 원칙(`add-claude-pr-review-action` Decision 6)과 어긋난다.

### Decision 10: 프롬프트 품질 요구사항을 `pr-auto-review` 스펙에 신규 Requirement로 추가한다

**선택**: `add-claude-pr-review-action`의 spec delta(`openspec/changes/add-claude-pr-review-action/specs/pr-auto-review/spec.md`)에 **"자동 리뷰 가이드라인 품질"** Requirement를 추가한다. (a) 저장소 루트에 `REVIEW.md`가 존재해야 함, (b) `REVIEW.md`가 Decisions 1–6 + 8의 핵심 항목(심각도 라벨, 🔴/🟡 경계 예시, high-signal 필터, 증거 인용, nit cap, 결과 없음 포맷, 출력 형식, 컨벤션 컨텍스트)을 포함해야 함, (c) `.github/workflows/claude-pr-review.yml`의 `prompt:` 블록이 `REVIEW.md`를 명시적으로 참조해야 함 — 세 가지를 SHALL/MUST 형태로 표현하고 각 항목별 Scenario를 둔다.

**근거**:

- `REVIEW.md`는 운영하면서 자주 미세 조정될 텍스트라 spec 수준의 핵심 원칙을 별도로 박아두지 않으면 회귀가 쉽게 생긴다.
- 새 협업자가 `REVIEW.md`를 수정할 때 spec을 먼저 보고 어떤 항목은 빼면 안 되는지 알 수 있다.
- 워크플로우와 `REVIEW.md`가 분리되어 있으므로 "참조 누락"(워크플로우는 그대로인데 `REVIEW.md`만 통째로 삭제 등)을 spec 차원에서 명시적으로 차단해야 한다.
- `pr-auto-review` 스펙은 아직 main specs에 동기화되지 않았으므로 delta 방식으로 한 번에 추가하기에 부담이 적다.

**대안 고려**:

- *`REVIEW.md` 본문만 바꾸고 spec은 손대지 않음*: 가장 빠르지만 회귀 방지 메커니즘이 사라진다.
- *별도 새 capability(`pr-review-prompt-quality`)로 분리*: capability 분할 비용이 크고, 트리거·인증과 같은 워크플로우의 한 측면이라 같은 capability에 두는 것이 자연스럽다.
- *`REVIEW.md`의 정확한 본문 텍스트를 spec에 박기*: spec이 본문 변경마다 갱신을 강요해 운영 부담이 커진다 — "다음 항목을 포함해야 한다" 수준의 추상도가 적정.

## Risks / Trade-offs

- **에이전트가 `REVIEW.md`를 읽지 않고 자체 추측으로 동작할 위험** → Mitigation: (a) 워크플로우 `prompt:` 블록 첫 줄에 "**먼저 저장소 루트의 `REVIEW.md`를 읽어라**"를 명령형으로 박는다. (b) 자체 PR 검증 단계에서 라벨/인용/cap이 누락된 코멘트가 나오면 즉시 감지된다. (c) 향후 액션이 `prompt-file:` 입력을 정식 지원하면 그쪽으로 전환해 의존도를 낮춘다.
- **`REVIEW.md` 위치가 워크플로우 prompt와 어긋나 참조가 깨질 수 있다** → Mitigation: spec Requirement에 "워크플로우의 `prompt:` 블록이 `REVIEW.md` 경로를 명시적으로 참조해야 한다"를 박아 두 파일의 동기화를 spec 차원에서 강제. 위치를 옮기는 변경은 spec delta와 함께 진행.
- **가이드라인이 길어져 입력 토큰이 증가한다** → Mitigation: high-signal 필터·nit cap·결과 없음 포맷이 출력 토큰을 줄여 순 효과는 중립~감소로 예상. `REVIEW.md` 분리로 매 PR 동일 컨텍스트가 캐시에 잘 맞아 비용 효율이 좋아진다. 첫 5~10개 PR에서 토큰 사용량(워크플로우 로그) + false positive 비율을 관찰해 컨벤션 컨텍스트를 더 줄일지 결정.
- **LLM이 심각도 라벨을 일관되게 매기지 못할 수 있다** (예: 머지 가능한 가벼운 누락 테스트를 🔴로 매김) → Mitigation: Decision 1의 "🔴 vs 🟡 경계" 서브섹션을 `REVIEW.md`에 그대로 옮겨 기존 테스트 코드 인용을 통한 구체 예시를 제공. 운영 후 잘못 분류된 사례가 누적되면 예시 목록을 정련.
- **Nit cap이 진짜 중요한 nit를 자를 수 있다** → Mitigation: 🟡로 분류된 시점에 이미 머지를 막을 수준이 아니라는 의미라 잘려도 회복 가능한 손실이다. 🔴는 cap 적용 안 함으로 안전 범위를 보장.
- **🟣 Pre-existing 라벨을 "이번 PR과 무관"으로 잘못 사용해 작성자가 도입한 결함을 놓칠 위험** → Mitigation: `REVIEW.md`에서 "PR diff에 추가된 라인이면 🟣 사용 금지"를 명시하고, `gh search`/`gh issue list`로 사전 존재 근거를 잡으라고 지시.
- **컨벤션 컨텍스트가 stale해질 수 있다**(헥사고날 모듈 추가/이름 변경 시 `REVIEW.md` 동기화 누락) → Mitigation: spec Requirement에 "프로젝트 컨벤션 컨텍스트가 `REVIEW.md`에 포함되어야 한다"만 박아두고 세부 목록은 `REVIEW.md` 단일 소스. 컨벤션 변경 PR에서 `REVIEW.md` 갱신을 PR 체크리스트로 권장.
- **`Bash(gh search:*)` / `Bash(gh issue list:*)` 추가로 액션의 도구 표면이 늘어난다** → Mitigation: 두 명령 모두 read-only, 코멘트 게시·라벨 변경 같은 쓰기 작업이 없어 OAuth 토큰의 영향 범위를 늘리지 않는다. `add-claude-pr-review-action`에서 정의한 권한(`pull-requests: write`)도 변경하지 않는다.
- **`pr-auto-review` 스펙이 두 개의 활성 변경에서 동시에 수정된다** (`add-claude-pr-review-action` 아카이브 전 + `improve-pr-review-prompt`) → Mitigation: 이 변경의 delta는 ADDED Requirements로만 구성해 base spec과 충돌하지 않게 설계. `add-claude-pr-review-action`이 먼저 아카이브되어 main specs에 반영된 뒤 본 변경의 delta가 적용되는 순서를 Migration Plan에 명시.

## Migration Plan

1. **`add-claude-pr-review-action` 변경 완료 확인**:
   - 본 변경은 `add-claude-pr-review-action`이 main specs에 동기화되어 있다는 것을 전제로 한다. 두 변경이 동시에 활성 상태이면 본 변경의 spec delta는 후행 정렬이 필요하다.
   - 정렬 순서: (a) `add-claude-pr-review-action` 머지 → (b) 해당 변경 아카이브 → (c) `pr-auto-review` 스펙이 `openspec/specs/pr-auto-review/spec.md`로 동기화 → (d) 본 변경의 spec delta 적용.

2. **`REVIEW.md` 신규 작성**:
   - 저장소 루트에 `REVIEW.md` 추가 — Decisions 1–6 + 8(컨벤션 컨텍스트) + Decision 1의 "🔴 vs 🟡 경계 예시"를 그대로 반영.
   - 기존 테스트 코드 인용(예: `RoomJoinIntegrationTest.kt:115-147`)도 그대로 포함해 LLM이 문서 안에서 직접 패턴을 따라갈 수 있도록 한다.

3. **워크플로우 prompt 블록 재작성**:
   - `.github/workflows/claude-pr-review.yml`의 `prompt:` 블록을 PR 컨텍스트(`REPO`, `PR NUMBER`) + "**먼저 저장소 루트의 `REVIEW.md`를 읽고 그 가이드 전체를 따라 이 PR을 리뷰하라**" 명령형 지시문으로 단순화 (Decision 7).
   - `claude_args.--allowedTools`에 `Bash(gh search:*)`, `Bash(gh issue list:*)` 추가 (Decision 9).
   - 같은 PR에서 `REVIEW.md` + 워크플로우 변경 + spec delta(`openspec/changes/improve-pr-review-prompt/specs/pr-auto-review/spec.md`)를 함께 머지.

4. **자체 PR로 동작 확인**:
   - 본 변경 PR 자체가 워크플로우를 트리거하므로 새 가이드라인이 자기 자신을 리뷰하게 된다.
   - 결과 코멘트에서 라벨·인용·cap이 의도대로 적용됐는지 직접 검증.
   - 특히 에이전트가 `REVIEW.md`를 실제로 읽었는지(코멘트 표현이 가이드라인 용어를 그대로 사용하는지)를 확인.
   - 결함이 없는 케이스를 만들기 어려우면 후속 작은 PR(예: 문서 오타 수정)로 결과 없음 포맷도 검증.

5. **운영 관찰**:
   - 머지 후 5~10개 PR에서 (a) false positive 비율, (b) 코멘트 평균 개수, (c) 토큰 사용량(워크플로우 로그), (d) `Read`로 `REVIEW.md`를 호출한 빈도를 관찰.
   - 라벨 분포가 한쪽으로 쏠리거나 nit cap이 의미 없는 빈도로 발동하면 후속 변경으로 임계 조정.

**롤백 전략**:

- 워크플로우 YAML + `REVIEW.md` + spec delta 세 파일의 변경이라 단일 revert PR로 즉시 이전 프롬프트로 복귀 가능. `REVIEW.md` 단독 삭제만으로는 워크플로우가 깨지므로 워크플로우와 함께 revert해야 한다는 점만 주의.
- 인프라·DB·코드 영향이 없어 데이터 마이그레이션 롤백은 불필요.
- 만약 새 가이드라인이 false positive를 폭발적으로 늘리면, 즉시 revert 후 high-signal 필터 항목만 단계적으로 재도입하는 점진적 롤아웃을 후속 변경으로 검토.

## Open Questions

- `anthropics/claude-code-action@v1`이 `prompt-file:` 같은 입력으로 `REVIEW.md`를 직접 주입하는 옵션을 정식 지원하는지 — 지원한다면 에이전트의 `Read` 도구 호출에 의존하지 않고 더 결정적인 주입이 가능. 액션 문서/릴리스 노트로 사전 검증 후 후속 변경으로 전환 검토.
- `REVIEW.md`의 위치를 저장소 루트로 시작했지만, 자동화 설정 파일들이 `.github/`에 모여 있는 패턴과 어긋난다는 의견이 나오면 `.github/CLAUDE_REVIEW.md`로 이전할지 — 자체 PR 리뷰 결과와 협업자 피드백을 보고 결정.
- spec Requirement에서 `REVIEW.md`의 본문 항목을 어느 추상도까지 박을지 — "심각도 라벨이 존재해야 한다" 정도가 최소, "🔴/🟡/🟣 3단계여야 한다"가 중간, "각 라벨 정의가 다음 항목을 포함해야 한다"가 최대. 현재 설계는 중간 추상도이며, 운영 후 라벨 체계가 흔들리면 더 강하게 박는 방안 검토.
