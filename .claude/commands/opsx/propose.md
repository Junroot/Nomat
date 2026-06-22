---
name: "OPSX: Propose"
description: 새 변경을 제안한다 - 변경을 만들고 모든 아티팩트를 한 번에 생성한다
category: Workflow
tags: [workflow, artifacts, experimental]
---

새 변경을 제안한다 - 변경을 만들고 모든 아티팩트를 한 번에 생성한다.

다음 아티팩트와 함께 변경을 만든다.
- proposal.md (무엇을 & 왜)
- design.md (어떻게)
- tasks.md (구현 단계)

구현할 준비가 되면 /opsx:apply를 실행하라.

---

**입력**: `/opsx:propose` 뒤의 인자는 변경 이름(kebab-case)이거나 사용자가 만들고자 하는 내용에 대한 설명이다.

**단계**

1. **입력이 없으면 무엇을 만들지 물어라**

   **AskUserQuestion tool**(선택지 없는 자유 응답)로 다음을 물어라.
   > "어떤 변경을 작업하고 싶나요? 만들거나 고치고 싶은 내용을 설명해 주세요."

   설명에서 kebab-case 이름을 도출하라(예: "add user authentication" → `add-user-auth`).

   **중요**: 사용자가 무엇을 만들려는지 이해하기 전에는 진행하지 마라.

2. **변경 디렉터리를 만들어라**
   ```bash
   openspec new change "<name>"
   ```
   이렇게 하면 `.openspec.yaml`과 함께 `openspec/changes/<name>/`에 변경 스캐폴드가 생성된다.

3. **아티팩트 생성 순서를 받아라**
   ```bash
   openspec status --change "<name>" --json
   ```
   JSON을 파싱해 다음을 얻어라.
   - `applyRequires`: 구현 전에 필요한 아티팩트 ID 배열(예: `["tasks"]`)
   - `artifacts`: 상태와 의존성이 담긴 전체 아티팩트 목록

4. **apply 가능 상태가 될 때까지 순서대로 아티팩트를 만들어라**

   **TodoWrite tool**로 아티팩트 진행 상황을 추적하라.

   의존성 순서로 아티팩트를 순회하라(미해결 의존성이 없는 아티팩트부터).

   a. **`ready` 상태인(의존성이 충족된) 각 아티팩트에 대해**:
      - 지침을 받아라:
        ```bash
        openspec instructions <artifact-id> --change "<name>" --json
        ```
      - 지침 JSON에는 다음이 포함된다:
        - `context`: 프로젝트 배경(당신을 위한 제약 - 출력에 포함하지 마라)
        - `rules`: 아티팩트별 규칙(당신을 위한 제약 - 출력에 포함하지 마라)
        - `template`: 출력 파일에 사용할 구조
        - `instruction`: 이 아티팩트 유형에 대한 스키마별 지침
        - `outputPath`: 아티팩트를 쓸 위치
        - `dependencies`: 컨텍스트로 읽어야 할 완료된 아티팩트
      - 컨텍스트로 쓸 완료된 의존성 파일을 모두 읽어라
      - `template`을 구조로 삼아 아티팩트 파일을 만들어라
      - `context`와 `rules`를 제약으로 적용하라 - 단, 파일에 복사하지 마라
      - 간단한 진행 상황을 보여줘라: "Created <artifact-id>"

   b. **`applyRequires`의 모든 아티팩트가 완료될 때까지 계속하라**
      - 각 아티팩트를 만든 뒤 `openspec status --change "<name>" --json`을 다시 실행하라
      - `applyRequires`의 모든 아티팩트 ID가 artifacts 배열에서 `status: "done"`인지 확인하라
      - `applyRequires`의 모든 아티팩트가 done이면 멈춰라

   c. **아티팩트에 사용자 입력이 필요하면**(컨텍스트가 불명확하면):
      - **AskUserQuestion tool**로 명확히 하라
      - 그런 다음 생성을 계속하라

5. **최종 상태를 보여줘라**
   ```bash
   openspec status --change "<name>"
   ```

**출력**

모든 아티팩트를 완성한 뒤 다음을 요약하라.
- 변경 이름과 위치
- 만든 아티팩트 목록과 간단한 설명
- 준비된 것: "모든 아티팩트가 만들어졌습니다! 구현할 준비가 됐습니다."
- 안내: "`/opsx:apply`를 실행해 구현을 시작하세요."

**아티팩트 생성 가이드라인**

- 각 아티팩트 유형마다 `openspec instructions`의 `instruction` 필드를 따라라
- 각 아티팩트에 무엇이 담겨야 하는지는 스키마가 정의한다 - 그것을 따라라
- 새 아티팩트를 만들기 전에 의존성 아티팩트를 컨텍스트로 읽어라
- `template`을 출력 파일의 구조로 삼아 각 섹션을 채워라
- **중요**: `context`와 `rules`는 파일 내용이 아니라 당신을 위한 제약이다
  - `<context>`, `<rules>`, `<project_context>` 블록을 아티팩트에 복사하지 마라
  - 이것들은 당신이 쓰는 내용을 안내할 뿐, 출력에는 절대 나타나면 안 된다

**가드레일**
- 구현에 필요한 모든 아티팩트를 만들어라(스키마의 `apply.requires`가 정의한 대로)
- 새 아티팩트를 만들기 전에 항상 의존성 아티팩트를 읽어라
- 컨텍스트가 치명적으로 불명확하면 사용자에게 물어라 - 단, 흐름을 유지하기 위해 합리적인 판단을 우선하라
- 같은 이름의 변경이 이미 있으면 그것을 이어서 진행할지 새로 만들지 사용자에게 물어라
- 각 아티팩트 파일을 쓴 뒤 다음으로 넘어가기 전에 파일이 존재하는지 확인하라
