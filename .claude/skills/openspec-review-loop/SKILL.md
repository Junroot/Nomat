---
name: openspec-review-loop
description: OpenSpec change 산출물(proposal/specs/design/tasks)을 구현 전에 검증하고, 기계적 결함은 자동 수정하며 통과할 때까지 루프를 돈다. design.md를 추가/수정한 뒤(훅이 자동으로 안내), 또는 "이 change 검증하고 고쳐줘"·"산출물 검토 루프 돌려줘"·"구현 시작해도 되는지 봐줘" 요청 시 사용한다. 검증은 openspec-change-reviewer 서브에이전트가, 기계적 수정은 openspec-change-fixer 서브에이전트가 fresh 컨텍스트에서 수행한다. 코드와 스펙의 일치를 보는 구현 후 검증(openspec-verify-change)과는 다르며, 그 앞 단계인 산출물 자체의 품질을 본다.
license: MIT
metadata:
  author: nomat
  version: "1.0"
---

OpenSpec change 산출물을 **구현 전에** 검증→수정→재검증 루프로 게이트한다. 검증은 `openspec-change-reviewer`(읽기 전용), 기계적 수정은 `openspec-change-fixer`(편집)가 각각 fresh 컨텍스트 서브에이전트로 수행하고, 이 스킬은 그 둘을 오케스트레이션한다.

역할 분리를 fresh 컨텍스트로 두는 이유: 산출물 검토는 방금 작성한 맥락에 오염되지 않은 시선이어야 결함이 드러나고, 기계적 수정은 격리된 최소 편집으로 부작용을 줄인다. 단 **의도가 개입되는 결함은 자동 수정하지 않고** 사용자 맥락을 가진 당신(메인)이 사용자와 함께 처리한다.

## 이 스킬이 하지 않는 것

구현된 코드가 스펙·태스크와 맞는지는 보지 않는다. 그건 `openspec-verify-change`(구현 후 검증)의 역할이다. 이 스킬은 **구현 전, 산출물 자체의 품질**만 본다.

## 통과 기준 (루프 종료 조건)

`openspec validate --strict` 통과 **AND** 🔴 Critical-객관(criticalObjective) 0건. 🟡 Warning과 🔴 Critical-주관은 리포트에만 남기고 루프를 멈추지 않는다(주관 항목은 매 라운드 재판정으로 진동하기 때문).

---

## 절차

### 0. 락 설정 · change 확정

- 루프 진행 락을 만든다: `touch "$CLAUDE_PROJECT_DIR/.claude/.openspec-review-lock"`. **이 락이 무한 재귀의 실제 방어선이다** — 서브에이전트(reviewer/fixer)의 편집도 이 세션의 design 트리거 훅을 발동시켜 마커를 쌓지만, 루프가 도는 동안 락이 있어 Stop 훅 넛지가 억제된다. 이 스킬은 **어떤 경로로 끝나든 락을 반드시 지운다**(아래 3단계).
- 검증 대기 마커가 있으면 change 이름을 거기서 읽는다: `cat "$CLAUDE_PROJECT_DIR/.claude/.openspec-review-pending" 2>/dev/null`. 인자로 change 이름이 주어졌으면 그것을 우선한다.
- 이름이 없거나 모호하면 `openspec list --json`으로 활성 change를 확인하고, 여러 개면 **AskUserQuestion**으로 고르게 한다. 추측해서 자동 선택하지 않는다.
- 활성 change가 0개이거나(모두 archive) 이름이 비활성/해석 불가면: 락·마커를 지우고 "검증할 활성 change가 없다"고 알리고 **조용히 종료**(no-op). 아카이브 change는 다루지 않는다.

### 1. 구현 후 가드

`openspec status --change "<name>" --json`으로 tasks 진행을 보고, `git diff --stat`으로 이 change에 대응하는 코드 변경이 이미 있는지 확인한다. **tasks가 상당수 체크됐거나 관련 코드 diff가 있으면** 이미 구현 단계다 → 자동 수정을 하지 말고, 산출물을 고치면 코드와 어긋난다고 알린 뒤 `openspec-verify-change`(구현 후 검증)로 넘긴다. 락·마커를 지우고 종료한다.

### 2. 검증→수정 루프 (최대 3라운드)

`prevGateIds`(직전 라운드의 게이트 결함 지문 집합)를 빈 집합으로 두고 시작한다.

각 라운드:

**2a. 검증** — `Agent` tool로 `subagent_type: "openspec-change-reviewer"`를 실행한다. 프롬프트에 change 이름을 명확히 전달하고, "구조검증(openspec validate --strict)부터 의미·설계·config.yaml 규칙까지 점검하고, 차원별 한국어 리포트와 맨 끝 JSON verdict를 반환하라"고 지시한다.

**2b. JSON 파싱** — 리포트 맨 끝의 마지막 ```json 블록을 파싱한다. 파싱 실패하거나 필수 필드가 없으면 **맹목 재루프 금지**: 리포트를 사용자에게 그대로 보이고 루프를 중단한다(3으로).

**2c. 판정** — `verdict == "PASS"`(즉 `validatePassed && criticalObjective == 0`)면 루프 성공 종료(3으로).

**2d. 결함 분류** — `FAIL`이면 findings를 두 기준으로 본다(서로 독립):
- **수정 유형**: `fixType=mechanical`(무손실 자동 수정 대상, **severity 무관**) vs `fixType=intent`(사람만 해소).
- **게이트 여부**: `severity=critical && objective=true`인 finding이 통과를 막는다(= criticalObjective). **루프 종료·진동 가드는 이 게이트 집합만 본다.** 🟡 warning과 🔴-주관은 게이트가 아니다.

**2e. 기계적 결함 자동 수정** — `fixType=mechanical`인 finding이 하나라도 있으면(warning이어도) `Agent` tool로 `subagent_type: "openspec-change-fixer"`를 실행한다. 프롬프트에 change 이름·디렉터리와 **mechanical finding 목록만**(id/file/summary/suggestion) 전달한다. intent 결함은 절대 넘기지 않는다. 무손실 교정이므로 severity와 무관하게 이번에 정리한다.

**2f. 의도 개입 결함은 사람에게** — `fixType=intent`인 finding, 특히 **게이트를 막는 intent 결함(criticalObjective 중 fixType=intent)** 이 하나라도 있으면 자동으로는 절대 PASS에 도달할 수 없다(fixer가 intent를 안 고침). 이 결함들을 사용자에게 명확히 제시하고(각각 무엇을·왜·어떤 선택지) **루프를 중단해 사용자 결정을 기다린다**. 사용자가 방향을 주면 당신(메인)이 직접 그 산출물을 고치고(사용자 의도 반영), 이 스킬을 다시 돌려 재검증한다.

**2g. 재검증과 진동 가드** — 게이트를 막는 intent 결함이 없어(criticalObjective가 전부 mechanical이라 2e에서 처리됨) 자동 수렴이 가능한 경우에만 다음 라운드로 넘어간다. 재검증 전 다음 중 하나면 중단하고 남은 결함을 보고한다:
- fixer가 아무 파일도 안 고쳤다(0 diff) — `git diff --stat`으로 확인.
- 이번 라운드 criticalObjective 지문 집합에 `prevGateIds`에 없던 **새 🔴-객관 결함(회귀)** 이 생겼다.
- criticalObjective 수가 직전 라운드 대비 **엄격히 감소하지 않았다**(같거나 늘었다).

통과하면 현재 criticalObjective 지문을 `prevGateIds`에 저장하고 다음 라운드로(2a).

3라운드를 다 쓰고도 PASS가 아니면 중단하고 남은 결함을 보고한다.

### 3. 종료

- 루프가 수정을 했다면 **통합 diff를 제시**한다: `git diff -- openspec/changes/<name>/`. 커밋은 하지 않는다(사용자가 검토 후 직접).
- 결과를 간결히 요약한다: verdict(PASS/미달), 자동 수정한 결함, 사용자 처리가 필요한 게이트-의도 결함, 남은 warning.
- PASS면 구현 단계(`opsx:apply` / `openspec-apply-change`)로 넘어가도 좋다고 안내한다.
- **락과 마커를 반드시 지운다** — 성공·미달·사용자 대기·중단 등 **어떤 경로로 끝나든**: `rm -f "$CLAUDE_PROJECT_DIR/.claude/.openspec-review-lock" "$CLAUDE_PROJECT_DIR/.claude/.openspec-review-pending"`. 락을 남기면 다음 design.md 편집 때 Stop 훅이 계속 skip 해 시스템이 멈춘 것처럼 보인다(Stop 훅이 30분 지난 stale 락은 자동 무시하지만 이에 의존하지 말 것). 2f에서 사용자 결정을 기다리며 중단할 때도 락·마커를 지우고, 미해결 게이트-의도 결함이 남았음을 사용자에게 명확히 알린다.

## 폴백

`openspec-change-reviewer`/`openspec-change-fixer` 타입을 찾을 수 없다는 오류가 나면(에이전트를 방금 추가해 레지스트리에 아직 안 올라옴): `/reload-plugins` 또는 세션 재시작으로 등록된다. 그동안은 `subagent_type: "general-purpose"`로 실행하되 "`.claude/agents/openspec-change-reviewer.md`(또는 `openspec-change-fixer.md`)를 읽고 그 본문 지침을 글자 그대로 따르라"고 지시해 동일한 결과를 얻는다.

## 참고

활성 change 이름은 문자로 시작한다. `openspec status --change`·`instructions apply --change`는 날짜 프리픽스(아카이브) 이름을 거부하므로, 활성 change 이름을 전달한다.
