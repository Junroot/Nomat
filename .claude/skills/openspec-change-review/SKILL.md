---
name: openspec-change-review
description: OpenSpec change 산출물(proposal/specs/design/tasks)을 만든 뒤 구현 전에 품질을 검증한다. openspec으로 change를 새로 만들거나(propose/new/ff/continue) 산출물 작성을 마친 직후, 구현(apply)에 들어가기 전, "이 change 제대로 됐는지 봐줘"·"산출물 검토/검증해줘"·"구현 시작해도 되는지 봐줘" 같은 요청에 사용한다. openspec-change-reviewer 서브에이전트를 fresh 컨텍스트로 실행해 구조검증(openspec validate)과 의미적·설계 품질 검토를 함께 수행한다. 코드와 스펙의 일치를 보는 구현 후 검증(openspec-verify-change)과는 다르며, 그 앞 단계인 산출물 자체의 품질을 본다. change 산출물 생성 직후에는 사용자가 명시적으로 요청하지 않아도 이 검증을 권하라.
license: MIT
metadata:
  author: nomat
  version: "1.0"
---

OpenSpec change 산출물의 품질을 **구현 전에** 검증한다. 실제 검증은 `openspec-change-reviewer` 서브에이전트가 fresh 컨텍스트에서 수행한다 — 이 스킬은 검증할 change를 식별하고 그 에이전트를 실행한 뒤 결과를 사용자에게 전달하는 역할이다.

서브에이전트를 따로 두는 이유: 산출물 검토는 메인 대화 맥락(특히 산출물을 방금 작성한 맥락)에 오염되지 않은 시선으로 봐야 결함이 잘 드러난다. 그래서 결론만 메인으로 돌려받는다.

## 이 스킬이 하지 않는 것

구현된 코드가 스펙·태스크와 맞는지는 보지 않는다. 그건 `openspec-verify-change`(구현 후 검증)의 역할이다. 이 스킬은 **구현 전, 산출물 자체의 품질**만 본다. 혼동되면 사용자에게 어느 단계인지 확인한다.

## 단계

1. **검증할 change를 정한다**

   change 이름이 주어졌으면 그것을 쓴다. 안 주어졌고 대화 맥락에서 분명하면(방금 만든 change 등) 그것을 쓰되 사용자에게 한 번 확인한다.

   불분명하면 `openspec list --json`으로 목록을 가져와 **AskUserQuestion**으로 선택하게 한다. 추측해서 자동 선택하지 않는다. tasks가 아직 없거나 미완료인, 즉 구현 전 단계의 change를 우선 보여준다.

2. **서브에이전트를 실행한다**

   `Agent` tool로 `subagent_type: "openspec-change-reviewer"` 에이전트를 실행한다. 프롬프트에 검증할 change 이름을 명확히 전달한다. 예:

   > change "add-room-chat" 의 OpenSpec 산출물을 검증하라. 구조검증(openspec validate --strict)부터 의미적·설계 품질, openspec/config.yaml 프로젝트 규칙까지 점검하고, 차원별·심각도별 리포트와 구현 진입 가능 여부를 반환하라.

   **폴백**: `openspec-change-reviewer` 타입을 찾을 수 없다는 오류가 나면, 에이전트를 방금 추가해 레지스트리에 아직 안 올라온 것이다(`/reload-plugins`로 등록하거나 세션 재시작하면 해결). 그동안은 `subagent_type: "general-purpose"`로 실행하되 "`.claude/agents/openspec-change-reviewer.md`를 읽고 그 본문 지침을 글자 그대로 따라 이 change를 검증하라"고 지시해 동일한 결과를 얻는다.

   에이전트가 change를 못 찾는 등 입력이 부족하면, 1단계로 돌아가 change를 다시 확정한 뒤 재실행한다. (참고: 활성 change 이름은 문자로 시작한다. `openspec status --change`·`instructions apply --change`는 날짜 프리픽스 이름을 거부하므로, 아카이브가 아닌 활성 change 이름을 전달한다.)

3. **결과를 전달하고 다음 행동을 제안한다**

   서브에이전트가 돌려준 리포트를 사용자에게 그대로 전한다. 그 위에:
   - 🔴 Critical이 있으면 구현 진입 전 해소를 권하고, 어떤 산출물을 어떻게 고칠지 짚는다. 사용자가 원하면 수정까지 돕는다.
   - 🔴이 없으면 구현 단계(`openspec-apply-change` / `opsx:apply`)로 넘어가도 좋다고 안내한다.

   리포트 내용을 메인에서 다시 장황하게 재생성하지 말고, 서브에이전트 결론을 살려 간결히 정리한다.
