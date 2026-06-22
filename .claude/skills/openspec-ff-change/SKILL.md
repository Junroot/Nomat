---
name: openspec-ff-change
description: OpenSpec 아티팩트 생성을 빠르게 진행한다. 각 단계를 하나씩 거치지 않고 구현에 필요한 모든 아티팩트를 한 번에 만들고 싶을 때 사용한다.
license: MIT
compatibility: Requires openspec CLI.
metadata:
  author: openspec
  version: "1.0"
  generatedBy: "1.3.1"
---

아티팩트 생성을 빠르게 진행한다 - 구현을 시작하는 데 필요한 모든 것을 한 번에 생성한다.

**입력**: 사용자 요청에는 변경 이름(kebab-case) 또는 만들고 싶은 것에 대한 설명이 포함되어야 한다.

**단계**

1. **명확한 입력이 없으면 무엇을 만들지 물어본다**

   **AskUserQuestion tool**(선택지 없는 개방형)을 사용해 물어본다:
   > "어떤 변경 작업을 하고 싶나요? 만들거나 고치고 싶은 것을 설명해 주세요."

   설명에서 kebab-case 이름을 도출한다(예: "사용자 인증 추가" → `add-user-auth`).

   **중요**: 사용자가 무엇을 만들려는지 파악하지 못한 채 진행하지 마라.

2. **변경 디렉터리를 생성한다**
   ```bash
   openspec new change "<name>"
   ```
   `openspec/changes/<name>/`에 스캐폴딩된 변경을 생성한다.

3. **아티팩트 빌드 순서를 가져온다**
   ```bash
   openspec status --change "<name>" --json
   ```
   JSON을 파싱해 다음을 얻는다:
   - `applyRequires`: 구현 전에 필요한 아티팩트 ID 배열(예: `["tasks"]`)
   - `artifacts`: 상태와 의존성을 포함한 전체 아티팩트 목록

4. **apply 준비가 될 때까지 순서대로 아티팩트를 생성한다**

   **TodoWrite tool**을 사용해 아티팩트 진행 상황을 추적한다.

   의존성 순서대로 아티팩트를 순회한다(대기 중인 의존성이 없는 아티팩트부터):

   a. **`ready` 상태인(의존성이 충족된) 각 아티팩트에 대해**:
      - 지시를 가져온다:
        ```bash
        openspec instructions <artifact-id> --change "<name>" --json
        ```
      - 지시 JSON에는 다음이 포함된다:
        - `context`: 프로젝트 배경(너를 위한 제약 - 출력에 포함하지 마라)
        - `rules`: 아티팩트별 규칙(너를 위한 제약 - 출력에 포함하지 마라)
        - `template`: 출력 파일에 사용할 구조
        - `instruction`: 이 아티팩트 유형에 대한 스키마별 안내
        - `outputPath`: 아티팩트를 쓸 위치
        - `dependencies`: 컨텍스트로 읽어야 할 완료된 아티팩트
      - 완료된 의존성 파일을 읽어 컨텍스트로 삼는다
      - `template`을 구조로 삼아 아티팩트 파일을 생성한다
      - `context`와 `rules`를 제약으로 적용한다 - 다만 파일에 복사하지는 마라
      - 간단한 진행 표시를 보여준다: "✓ Created <artifact-id>"

   b. **모든 `applyRequires` 아티팩트가 완료될 때까지 계속한다**
      - 각 아티팩트를 생성한 뒤 `openspec status --change "<name>" --json`을 다시 실행한다
      - `applyRequires`의 모든 아티팩트 ID가 artifacts 배열에서 `status: "done"`인지 확인한다
      - 모든 `applyRequires` 아티팩트가 done이면 멈춘다

   c. **아티팩트가 사용자 입력을 필요로 하면**(컨텍스트가 불분명):
      - **AskUserQuestion tool**을 사용해 명확히 한다
      - 그런 다음 생성을 계속한다

5. **최종 상태를 보여준다**
   ```bash
   openspec status --change "<name>"
   ```

**출력**

모든 아티팩트를 완료한 뒤 요약한다:
- 변경 이름과 위치
- 생성한 아티팩트 목록과 간단한 설명
- 준비 상태: "모든 아티팩트를 생성했습니다! 구현 준비가 되었습니다."
- 안내: "`/opsx:apply`를 실행하거나 구현을 요청하면 작업을 시작합니다."

**아티팩트 생성 가이드라인**

- 각 아티팩트 유형에 대해 `openspec instructions`의 `instruction` 필드를 따른다
- 스키마가 각 아티팩트에 담길 내용을 정의한다 - 그대로 따른다
- 새 아티팩트를 만들기 전에 의존 아티팩트를 읽어 컨텍스트로 삼는다
- `template`을 출력 파일의 구조로 삼아 각 섹션을 채운다
- **중요**: `context`와 `rules`는 너를 위한 제약이지 파일에 담을 내용이 아니다
  - `<context>`, `<rules>`, `<project_context>` 블록을 아티팩트에 복사하지 마라
  - 이들은 네가 쓸 내용을 안내할 뿐, 출력에 절대 나타나서는 안 된다

**가드레일**
- 구현에 필요한 모든 아티팩트를 생성한다(스키마의 `apply.requires`가 정의한 대로)
- 새 아티팩트를 만들기 전에 항상 의존 아티팩트를 읽는다
- 컨텍스트가 치명적으로 불분명하면 사용자에게 묻는다 - 다만 추진력을 유지하기 위해 합리적인 결정을 우선한다
- 같은 이름의 변경이 이미 있으면 그 변경을 이어가도록 제안한다
- 각 아티팩트 파일을 쓴 뒤 다음으로 넘어가기 전에 파일이 존재하는지 확인한다
