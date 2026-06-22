---
name: openspec-verify-change
description: 구현이 change artifact와 일치하는지 검증한다. archive 전에 구현이 완전하고, 정확하고, 일관적인지 확인하려 할 때 사용한다.
license: MIT
compatibility: Requires openspec CLI.
metadata:
  author: openspec
  version: "1.0"
  generatedBy: "1.3.1"
---

구현이 change artifact(specs, tasks, design)와 일치하는지 검증한다.

**입력**: change 이름을 선택적으로 지정한다. 생략하면 대화 맥락에서 추론할 수 있는지 확인한다. 모호하거나 불분명하면 반드시 사용 가능한 change 목록을 제시해 선택을 요청해야 한다.

**단계**

1. **change 이름이 주어지지 않으면 선택을 요청한다**

   `openspec list --json`을 실행해 사용 가능한 change 목록을 가져온다. **AskUserQuestion tool**로 사용자가 선택하게 한다.

   구현 task가 있는 change(tasks artifact가 존재하는 change)를 보여준다.
   가능하면 각 change에 사용된 schema를 함께 표시한다.
   task가 미완료인 change는 "(In Progress)"로 표시한다.

   **중요**: change를 추측하거나 자동 선택하지 마라. 항상 사용자가 선택하게 한다.

2. **status를 확인해 schema 파악**
   ```bash
   openspec status --change "<name>" --json
   ```
   JSON을 파싱해 다음을 파악한다:
   - `schemaName`: 사용 중인 워크플로(예: "spec-driven")
   - 이 change에 어떤 artifact가 존재하는지

3. **change 디렉터리를 가져오고 artifact를 로드**

   ```bash
   openspec instructions apply --change "<name>" --json
   ```

   change 디렉터리와 `contextFiles`(artifact ID -> 실제 파일 경로 배열)를 반환한다. `contextFiles`에서 사용 가능한 모든 artifact를 읽는다.

4. **검증 리포트 구조 초기화**

   세 가지 차원으로 리포트 구조를 만든다:
   - **완전성(Completeness)**: task와 spec 커버리지를 추적
   - **정확성(Correctness)**: requirement 구현과 scenario 커버리지를 추적
   - **일관성(Coherence)**: 설계 준수와 패턴 일관성을 추적

   각 차원은 CRITICAL, WARNING, SUGGESTION 이슈를 가질 수 있다.

5. **완전성 검증**

   **Task 완료**:
   - `contextFiles.tasks`가 존재하면, 그 안의 모든 파일 경로를 읽는다
   - 체크박스를 파싱한다: `- [ ]`(미완료) vs `- [x]`(완료)
   - 완료 수 대비 전체 task 수를 센다
   - 미완료 task가 있으면:
     - 각 미완료 task에 대해 CRITICAL 이슈를 추가한다
     - 권장: "작업 완료: <description>" 또는 "이미 구현됐다면 완료로 표시"

   **Spec 커버리지**:
   - `openspec/changes/<name>/specs/`에 delta spec이 존재하면:
     - 모든 requirement("### Requirement:"로 표시됨)를 추출한다
     - 각 requirement에 대해:
       - requirement와 관련된 키워드를 코드베이스에서 검색한다
       - 구현이 존재할 가능성이 있는지 평가한다
     - requirement가 구현되지 않은 것으로 보이면:
       - CRITICAL 이슈 추가: "requirement를 찾을 수 없음: <requirement name>"
       - 권장: "requirement X 구현: <description>"

6. **정확성 검증**

   **Requirement 구현 매핑**:
   - delta spec의 각 requirement에 대해:
     - 코드베이스에서 구현 증거를 검색한다
     - 찾으면 파일 경로와 라인 범위를 기록한다
     - 구현이 requirement 의도와 일치하는지 평가한다
     - 차이가 감지되면:
       - WARNING 추가: "구현이 spec과 다를 수 있음: <details>"
       - 권장: "<file>:<lines>를 requirement X와 대조해 검토"

   **Scenario 커버리지**:
   - delta spec의 각 scenario("#### Scenario:"로 표시됨)에 대해:
     - 조건이 코드에서 처리되는지 확인한다
     - scenario를 다루는 테스트가 있는지 확인한다
     - scenario가 다뤄지지 않은 것으로 보이면:
       - WARNING 추가: "scenario가 다뤄지지 않음: <scenario name>"
       - 권장: "scenario에 대한 테스트 또는 구현 추가: <description>"

7. **일관성 검증**

   **설계 준수**:
   - `contextFiles.design`이 존재하면:
     - 핵심 결정을 추출한다("Decision:", "Approach:", "Architecture:" 같은 섹션을 찾는다)
     - 구현이 그 결정을 따르는지 검증한다
     - 모순이 감지되면:
       - WARNING 추가: "설계 결정이 지켜지지 않음: <decision>"
       - 권장: "구현을 수정하거나 design.md를 실제에 맞게 갱신"
   - design.md가 없으면: 설계 준수 검사를 건너뛰고 "대조할 design.md 없음"으로 기록한다

   **코드 패턴 일관성**:
   - 새 코드가 프로젝트 패턴과 일관적인지 검토한다
   - 파일 명명, 디렉터리 구조, 코딩 스타일을 확인한다
   - 큰 편차가 발견되면:
     - SUGGESTION 추가: "코드 패턴 편차: <details>"
     - 권장: "프로젝트 패턴을 따르는 것을 고려: <example>"

8. **검증 리포트 생성**

   **요약 스코어카드**:
   ```
   ## 검증 리포트: <change-name>

   ### 요약
   | 차원       | 상태               |
   |------------|--------------------|
   | 완전성     | task X/Y, req N개   |
   | 정확성     | req M/N개 커버됨    |
   | 일관성     | 준수/이슈          |
   ```

   **우선순위별 이슈**:

   1. **CRITICAL**(archive 전에 반드시 수정):
      - 미완료 task
      - 누락된 requirement 구현
      - 각각 구체적이고 실행 가능한 권장과 함께

   2. **WARNING**(수정해야 함):
      - spec/design 차이
      - 누락된 scenario 커버리지
      - 각각 구체적인 권장과 함께

   3. **SUGGESTION**(수정하면 좋음):
      - 패턴 불일치
      - 사소한 개선
      - 각각 구체적인 권장과 함께

   **최종 평가**:
   - CRITICAL 이슈가 있으면: "치명적 이슈 X건 발견. 아카이브 전 수정 필요."
   - WARNING만 있으면: "치명적 이슈 없음. 검토할 경고 Y건. 아카이브 준비됨(명시된 개선사항 포함)."
   - 모두 통과하면: "모든 검사 통과. 아카이브 준비됨."

**검증 휴리스틱**

- **완전성**: 객관적인 체크리스트 항목(체크박스, requirement 목록)에 집중한다
- **정확성**: 키워드 검색, 파일 경로 분석, 합리적 추론을 사용한다 - 완벽한 확신을 요구하지 않는다
- **일관성**: 명백한 불일치를 찾는다, 스타일을 트집 잡지 않는다
- **거짓 양성**: 불확실하면 WARNING보다 SUGGESTION을, CRITICAL보다 WARNING을 택한다
- **실행 가능성**: 모든 이슈는 해당하는 경우 파일/라인 참조와 함께 구체적인 권장을 가져야 한다

**점진적 축소(Graceful Degradation)**

- tasks.md만 있으면: task 완료만 검증하고 spec/design 검사는 건너뛴다
- tasks + specs가 있으면: 완전성과 정확성을 검증하고 design은 건너뛴다
- 전체 artifact가 있으면: 세 차원 모두 검증한다
- 어떤 검사를 왜 건너뛰었는지 항상 기록한다

**출력 형식**

다음을 포함한 명확한 마크다운을 사용한다:
- 요약 스코어카드용 표
- 이슈를 그룹화한 목록(CRITICAL/WARNING/SUGGESTION)
- 코드 참조는 `file.ts:123` 형식으로
- 구체적이고 실행 가능한 권장
- "검토를 고려해보라" 같은 모호한 제안 금지
