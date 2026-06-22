---
name: openspec-bulk-archive-change
description: 완료된 여러 변경을 한 번에 아카이브한다. 병렬로 진행한 여러 변경을 아카이브할 때 사용한다.
license: MIT
compatibility: Requires openspec CLI.
metadata:
  author: openspec
  version: "1.0"
  generatedBy: "1.3.1"
---

완료된 여러 변경을 한 번의 작업으로 아카이브한다.

이 스킬은 여러 변경을 일괄 아카이브하며, 실제로 무엇이 구현됐는지 코드베이스를 확인해 spec 충돌을 지능적으로 처리한다.

**입력**: 없음 (선택을 위한 프롬프트를 띄운다)

**단계**

1. **활성 변경 가져오기**

   `openspec list --json`을 실행해 모든 활성 변경을 가져온다.

   활성 변경이 없으면 사용자에게 알리고 중단한다.

2. **변경 선택 프롬프트**

   **AskUserQuestion tool**을 다중 선택으로 사용해 사용자가 변경을 고르게 한다:
   - 각 변경을 schema와 함께 보여준다
   - "모든 변경" 옵션을 포함한다
   - 선택 개수에 제한을 두지 않는다 (1개 이상이면 동작하고, 2개 이상이 일반적인 사용 사례다)

   **중요**: 자동으로 선택하지 마라. 항상 사용자가 고르게 하라.

3. **일괄 검증 - 선택된 모든 변경의 상태 수집**

   선택된 각 변경에 대해 다음을 수집한다:

   a. **아티팩트 상태** - `openspec status --change "<name>" --json` 실행
      - `schemaName`과 `artifacts` 목록을 파싱한다
      - 어떤 아티팩트가 `done`이고 어떤 것이 다른 상태인지 확인한다

   b. **태스크 완료 여부** - `openspec/changes/<name>/tasks.md` 읽기
      - `- [ ]`(미완료)와 `- [x]`(완료) 개수를 센다
      - tasks 파일이 없으면 "No tasks"로 기록한다

   c. **델타 spec** - `openspec/changes/<name>/specs/` 디렉터리 확인
      - 어떤 capability spec이 있는지 나열한다
      - 각 spec에서 요구사항 이름을 추출한다 (`### Requirement: <name>` 형태의 줄)

4. **spec 충돌 감지**

   `capability -> [해당 capability를 건드리는 변경들]` 맵을 만든다:

   ```
   auth -> [change-a, change-b]  <- CONFLICT (2+ changes)
   api  -> [change-c]            <- OK (only 1 change)
   ```

   선택된 변경 2개 이상이 같은 capability의 델타 spec을 가질 때 충돌이 발생한다.

5. **에이전트 방식으로 충돌 해결**

   **각 충돌에 대해** 코드베이스를 조사한다:

   a. 충돌하는 각 변경의 **델타 spec을 읽어** 각각 무엇을 추가/수정한다고 주장하는지 파악한다

   b. 구현 증거를 찾기 위해 **코드베이스를 검색한다**:
      - 각 델타 spec의 요구사항을 구현한 코드를 찾는다
      - 관련 파일, 함수, 테스트가 있는지 확인한다

   c. **해결 방안 결정**:
      - 한 변경만 실제로 구현됐으면 -> 그 변경의 spec만 동기화한다
      - 둘 다 구현됐으면 -> 시간순으로 적용한다 (오래된 것 먼저, 새것이 덮어쓴다)
      - 어느 것도 구현되지 않았으면 -> spec 동기화를 건너뛰고 사용자에게 경고한다

   d. 각 충돌의 **해결 방안 기록**:
      - 어느 변경의 spec을 적용할지
      - 어떤 순서로 (둘 다라면)
      - 근거 (코드베이스에서 무엇을 찾았는지)

6. **통합 상태 표 표시**

   모든 변경을 요약하는 표를 보여준다:

   ```
   | 변경                | Artifacts | Tasks | Specs    | 충돌     | 상태   |
   |---------------------|-----------|-------|----------|----------|--------|
   | schema-management   | 완료      | 5/5   | delta 2개| 없음     | 준비됨 |
   | project-config      | 완료      | 3/3   | delta 1개| 없음     | 준비됨 |
   | add-oauth           | 완료      | 4/4   | delta 1개| auth (!) | 준비됨*|
   | add-verify-skill    | 1개 남음  | 2/5   | 없음     | 없음     | 경고   |
   ```

   충돌의 경우 해결 방안을 보여준다:
   ```
   * 충돌 해결:
     - auth spec: add-oauth 적용 후 add-jwt 적용 (둘 다 구현됨, 시간순)
   ```

   미완료 변경의 경우 경고를 보여준다:
   ```
   경고:
   - add-verify-skill: 미완료 artifact 1개, 미완료 task 3개
   ```

7. **일괄 작업 확인**

   **AskUserQuestion tool**로 한 번의 확인을 받는다:

   - 상태에 따라 옵션을 구성한 "변경 N개를 아카이브할까요?"
   - 옵션 예시:
     - "변경 N개 모두 아카이브"
     - "준비된 N개만 아카이브 (미완료 건너뛰기)"
     - "취소"

   미완료 변경이 있으면 경고와 함께 아카이브된다는 점을 분명히 한다.

8. **확인된 각 변경에 대해 아카이브 실행**

   결정된 순서대로 변경을 처리한다 (충돌 해결 순서를 따른다):

   a. 델타 spec이 있으면 **spec 동기화**:
      - openspec-sync-specs 방식(에이전트 주도의 지능형 병합)을 사용한다
      - 충돌은 해결된 순서대로 적용한다
      - 동기화 수행 여부를 추적한다

   b. **아카이브 수행**:
      ```bash
      mkdir -p openspec/changes/archive
      mv openspec/changes/<name> openspec/changes/archive/YYYY-MM-DD-<name>
      ```

   c. 각 변경의 **결과 추적**:
      - 성공: 정상적으로 아카이브됨
      - 실패: 아카이브 중 오류 (오류 기록)
      - 건너뜀: 사용자가 아카이브하지 않기로 선택 (해당하는 경우)

9. **요약 표시**

   최종 결과를 보여준다:

   ```
   ## 일괄 아카이브 완료

   3개 변경 아카이브:
   - schema-management-cli -> archive/2026-01-19-schema-management-cli/
   - project-config -> archive/2026-01-19-project-config/
   - add-oauth -> archive/2026-01-19-add-oauth/

   1개 변경 건너뜀:
   - add-verify-skill (사용자가 미완료 아카이브를 원하지 않음)

   spec 동기화 요약:
   - delta spec 4개를 메인 spec에 동기화
   - 충돌 1건 해결 (auth: 시간순으로 둘 다 적용)
   ```

   실패가 있으면:
   ```
   1개 변경 실패:
   - some-change: 아카이브 디렉터리가 이미 존재함
   ```

**충돌 해결 예시**

예시 1: 하나만 구현됨
```
충돌: specs/auth/spec.md를 [add-oauth, add-jwt]가 건드림

add-oauth 확인:
- delta가 "OAuth Provider Integration" requirement를 추가함
- 코드베이스 검색 중... OAuth 흐름을 구현한 src/auth/oauth.ts 발견

add-jwt 확인:
- delta가 "JWT Token Handling" requirement를 추가함
- 코드베이스 검색 중... JWT 구현 없음

해결: add-oauth만 구현됨. add-oauth spec만 동기화한다.
```

예시 2: 둘 다 구현됨
```
충돌: specs/api/spec.md를 [add-rest-api, add-graphql]가 건드림

add-rest-api 확인 (2026-01-10 생성):
- delta가 "REST Endpoints" requirement를 추가함
- 코드베이스 검색 중... src/api/rest.ts 발견

add-graphql 확인 (2026-01-15 생성):
- delta가 "GraphQL Schema" requirement를 추가함
- 코드베이스 검색 중... src/api/graphql.ts 발견

해결: 둘 다 구현됨. add-rest-api spec을 먼저 적용하고,
이어서 add-graphql spec을 적용한다 (시간순, 새것이 우선).
```

**성공 시 출력**

```
## 일괄 아카이브 완료

N개 변경 아카이브:
- <change-1> -> archive/YYYY-MM-DD-<change-1>/
- <change-2> -> archive/YYYY-MM-DD-<change-2>/

spec 동기화 요약:
- delta spec N개를 메인 spec에 동기화
- 충돌 없음 (또는: 충돌 M건 해결)
```

**부분 성공 시 출력**

```
## 일괄 아카이브 완료(부분)

N개 변경 아카이브:
- <change-1> -> archive/YYYY-MM-DD-<change-1>/

M개 변경 건너뜀:
- <change-2> (사용자가 미완료 아카이브를 원하지 않음)

K개 변경 실패:
- <change-3>: 아카이브 디렉터리가 이미 존재함
```

**변경이 없을 때의 출력**

```
## 아카이브할 변경 없음

활성 변경이 없다. 새 변경을 만들어 시작하라.
```

**가드레일**
- 변경 개수에 제한을 두지 마라 (1개 이상이면 되고, 2개 이상이 일반적인 사용 사례다)
- 항상 선택 프롬프트를 띄우고, 자동으로 선택하지 마라
- spec 충돌을 일찍 감지하고 코드베이스를 확인해 해결하라
- 두 변경 모두 구현됐으면 spec을 시간순으로 적용하라
- 구현이 없을 때만 spec 동기화를 건너뛰어라 (사용자에게 경고)
- 확인 전에 변경별 상태를 명확히 보여줘라
- 전체 배치에 대해 한 번의 확인을 사용하라
- 모든 결과(성공/건너뜀/실패)를 추적하고 보고하라
- 아카이브로 옮길 때 .openspec.yaml을 보존하라
- 아카이브 디렉터리 대상은 현재 날짜를 사용한다: YYYY-MM-DD-<name>
- 아카이브 대상이 이미 존재하면 해당 변경은 실패 처리하되 나머지는 계속 진행하라
