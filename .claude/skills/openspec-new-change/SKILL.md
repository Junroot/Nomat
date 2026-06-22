---
name: openspec-new-change
description: 실험적 아티팩트 워크플로로 새 OpenSpec 변경을 시작한다. 사용자가 구조화된 단계별 접근으로 새 기능, 수정, 변경을 만들고자 할 때 사용한다.
license: MIT
compatibility: Requires openspec CLI.
metadata:
  author: openspec
  version: "1.0"
  generatedBy: "1.3.1"
---

실험적 아티팩트 기반 접근으로 새 변경을 시작한다.

**입력**: 사용자의 요청에는 변경 이름(kebab-case)이나 만들고자 하는 내용에 대한 설명이 담겨 있어야 한다.

**단계**

1. **명확한 입력이 없으면 무엇을 만들지 물어라**

   **AskUserQuestion tool**(선택지 없는 자유 응답)로 다음을 물어라.
   > "어떤 변경을 작업하고 싶나요? 만들거나 고치고 싶은 내용을 설명해 주세요."

   설명에서 kebab-case 이름을 도출하라(예: "add user authentication" → `add-user-auth`).

   **중요**: 사용자가 무엇을 만들려는지 이해하기 전에는 진행하지 마라.

2. **워크플로 스키마를 정하라**

   사용자가 다른 워크플로를 명시적으로 요청하지 않는 한 기본 스키마를 사용한다(`--schema` 생략).

   **다음 경우에만 다른 스키마를 사용하라:**
   - 특정 스키마 이름을 언급함 → `--schema <name>` 사용
   - "show workflows" 또는 "what workflows" → `openspec schemas --json`을 실행해 사용자가 고르게 하라

   **그 외에는**: `--schema`를 생략해 기본 스키마를 사용하라.

3. **변경 디렉터리를 만들어라**
   ```bash
   openspec new change "<name>"
   ```
   사용자가 특정 워크플로를 요청한 경우에만 `--schema <name>`을 추가하라.
   이렇게 하면 선택한 스키마로 `openspec/changes/<name>/`에 변경 스캐폴드가 생성된다.

4. **아티팩트 상태를 보여줘라**
   ```bash
   openspec status --change "<name>"
   ```
   어떤 아티팩트를 만들어야 하고 어떤 것이 준비됐는지(의존성 충족) 보여준다.

5. **첫 아티팩트의 지침을 받아라**
   첫 아티팩트는 스키마에 따라 다르다(예: spec-driven의 경우 `proposal`).
   상태 출력에서 status가 "ready"인 첫 아티팩트를 찾아라.
   ```bash
   openspec instructions <first-artifact-id> --change "<name>"
   ```
   첫 아티팩트를 만들기 위한 템플릿과 컨텍스트를 출력한다.

6. **멈추고 사용자 지시를 기다려라**

**출력**

단계를 마친 뒤 다음을 요약하라.
- 변경 이름과 위치
- 사용 중인 스키마/워크플로와 아티팩트 순서
- 현재 상태(아티팩트 0/N 완료)
- 첫 아티팩트의 템플릿
- 안내: "첫 아티팩트를 만들 준비가 됐나요? 이 변경이 무엇에 관한 것인지 설명해 주면 초안을 작성하고, 아니면 계속 진행하라고 말해 주세요."

**가드레일**
- 아직 아티팩트를 만들지 마라 - 지침만 보여줘라
- 첫 아티팩트 템플릿을 보여주는 단계 이상으로 진행하지 마라
- 이름이 유효하지 않으면(kebab-case가 아니면) 유효한 이름을 요청하라
- 같은 이름의 변경이 이미 있으면 그 변경을 이어서 진행하라고 제안하라
- 기본이 아닌 워크플로를 사용하면 --schema를 전달하라
