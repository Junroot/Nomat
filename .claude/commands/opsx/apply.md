---
name: "OPSX: Apply"
description: OpenSpec change의 task를 구현한다 (실험적)
category: Workflow
tags: [workflow, artifacts, experimental]
---

OpenSpec change의 task를 구현한다.

**입력**: change 이름을 선택적으로 지정한다(예: `/opsx:apply add-auth`). 생략하면 대화 맥락에서 추론할 수 있는지 확인한다. 모호하거나 불분명하면 반드시 사용 가능한 change 목록을 제시해 선택을 요청해야 한다.

**단계**

1. **change 선택**

   이름이 주어지면 그것을 사용한다. 그렇지 않으면:
   - 사용자가 change를 언급했다면 대화 맥락에서 추론한다
   - 활성 change가 하나뿐이면 자동으로 선택한다
   - 모호하면 `openspec list --json`을 실행해 사용 가능한 change 목록을 가져오고 **AskUserQuestion tool**로 사용자가 선택하게 한다

   항상 "Using change: <name>"과 재정의 방법(예: `/opsx:apply <other>`)을 알린다.

2. **status를 확인해 schema 파악**
   ```bash
   openspec status --change "<name>" --json
   ```
   JSON을 파싱해 다음을 파악한다:
   - `schemaName`: 사용 중인 워크플로(예: "spec-driven")
   - 어떤 artifact에 task가 들어 있는지(spec-driven은 보통 "tasks", 그 외는 status에서 확인)

3. **apply 지침 가져오기**

   ```bash
   openspec instructions apply --change "<name>" --json
   ```

   다음을 반환한다:
   - `contextFiles`: artifact ID -> 실제 파일 경로 배열(schema에 따라 다름)
   - 진행 상황(전체, 완료, 남은 수)
   - 상태가 표시된 task 목록
   - 현재 상태 기반의 동적 지침

   **상태 처리:**
   - `state: "blocked"`(artifact 누락)이면: 메시지를 보여주고 `/opsx:continue` 사용을 제안한다
   - `state: "all_done"`이면: 축하하고 archive를 제안한다
   - 그 외에는: 구현으로 진행한다

4. **컨텍스트 파일 읽기**

   apply 지침 출력의 `contextFiles`에 나열된 모든 파일 경로를 읽는다.
   파일은 사용 중인 schema에 따라 다르다:
   - **spec-driven**: proposal, specs, design, tasks
   - 그 외 schema: CLI 출력의 contextFiles를 따른다

5. **현재 진행 상황 표시**

   다음을 보여준다:
   - 사용 중인 schema
   - 진행 상황: "N/M tasks complete"
   - 남은 task 개요
   - CLI의 동적 지침

6. **task 구현(완료되거나 막힐 때까지 반복)**

   각 대기 중인 task에 대해:
   - 어떤 task를 작업 중인지 보여준다
   - 필요한 코드 변경을 한다
   - 변경은 최소한으로 집중해서 한다
   - tasks 파일에서 task를 완료로 표시한다: `- [ ]` → `- [x]`
   - 다음 task로 넘어간다

   **다음의 경우 멈춘다:**
   - task가 불분명하면 → 설명을 요청한다
   - 구현 중 설계 문제가 드러나면 → artifact 업데이트를 제안한다
   - 오류나 차단 요소를 만나면 → 보고하고 안내를 기다린다
   - 사용자가 중단시키면

7. **완료 또는 일시 정지 시 상태 표시**

   다음을 보여준다:
   - 이번 세션에서 완료한 task
   - 전체 진행 상황: "N/M tasks complete"
   - 모두 완료되면: archive를 제안한다
   - 일시 정지되면: 이유를 설명하고 안내를 기다린다

**구현 중 출력**

```
## 구현 중: <change-name> (schema: <schema-name>)

작업 진행 중 3/7: <task description>
[...구현 진행 중...]
✓ 작업 완료

작업 진행 중 4/7: <task description>
[...구현 진행 중...]
✓ 작업 완료
```

**완료 시 출력**

```
## 구현 완료

**변경:** <change-name>
**스키마:** <schema-name>
**진행:** 작업 7/7 완료 ✓

### 이번 세션에서 완료
- [x] Task 1
- [x] Task 2
...

모든 작업 완료! `/opsx:archive`로 이 change를 아카이브할 수 있다.
```

**일시 정지 시 출력(문제 발생)**

```
## 구현 일시 정지

**변경:** <change-name>
**스키마:** <schema-name>
**진행:** 작업 4/7 완료

### 발생한 문제
<description of the issue>

**선택지:**
1. <option 1>
2. <option 2>
3. 다른 접근

어떻게 진행할까?
```

**가드레일**
- 완료되거나 막힐 때까지 task를 계속 진행한다
- 시작 전 항상 컨텍스트 파일을 읽는다(apply 지침 출력에서)
- task가 모호하면 멈추고 구현 전에 물어본다
- 구현 중 문제가 드러나면 멈추고 artifact 업데이트를 제안한다
- 코드 변경은 최소한으로, 각 task 범위에 한정한다
- 각 task를 완료한 직후 task 체크박스를 업데이트한다
- 오류, 차단 요소, 불분명한 요구사항을 만나면 멈춘다 - 추측하지 않는다
- CLI 출력의 contextFiles를 사용하고, 특정 파일 이름을 가정하지 않는다

**유연한 워크플로 통합**

이 스킬은 "change에 대한 동작" 모델을 지원한다:

- **언제든 호출 가능**: 모든 artifact가 완성되기 전에도(task가 있다면), 부분 구현 후에도, 다른 동작과 섞어서도
- **artifact 업데이트 허용**: 구현 중 설계 문제가 드러나면 artifact 업데이트를 제안한다 - 단계에 고정되지 않고 유연하게 작업한다
