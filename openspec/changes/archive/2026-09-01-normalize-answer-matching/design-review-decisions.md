# design-review 결정 원장

## [치명] 선행 change가 세운 스펙 요구사항을 정면으로 뒤집는데 delta 가 ADDED 뿐이다

- 심각도: [치명]
- 질문 요지: `fix-additional-title-collation` 과 `normalize-answer-matching` 이 같은 capability `playlist-track-answer` 에 상반된 SHALL 을 각각 ADDED 로 넣어, 두 change 가 아카이브되면 capability 가 자기모순이 된다. 선행 요구사항을 (1) 지금 재진술할지 (2) 본 change 에서 MODIFIED 로 좁힐지 (3) REMOVED 로 대체할지 결정 요청.
- 사용자 결정: **(1) 선행 change 를 지금 재진술한다.** `fix-additional-title-collation` 은 아직 구현 전(0/13)이고 아카이브도 되지 않았으므로, 그 요구사항을 "저장소가 거부하지 않는다"는 **저장 계층 보증**으로 지금 다시 쓴다. 가나 시나리오는 빼고 탁점 시나리오만 남긴다 — 탁점은 `normalize-answer-matching` 이 접지 않는 축이라 두 change 이후에도 영구히 참이다. 양쪽 delta 는 `## ADDED Requirements` 그대로 유지되어 capability 이력에 MODIFIED 흔적이 남지 않고, 깨질 테스트(선행 tasks 2.2)를 애초에 만들지 않게 된다.
- 반영 방향:
  1. `openspec/changes/fix-additional-title-collation/specs/playlist-track-answer/spec.md` 수정
     - 요구사항 본문을 **저장 계층 보증**으로 재진술한다: 저장소(MySQL)의 문자열 비교 규칙이 표기가 다른 값을 동일시해 저장을 거부해서는 안 된다(MUST NOT). "다음 축에서만 다른 두 값은 서로 다른 추가 정답이며 한 트랙에 함께 저장될 수 있어야 한다(SHALL)"처럼 **API 관측 결과로 보장하는 문장은 제거**한다. 축 목록을 남기려면 "저장소가 동일시하는 축"(= `utf8mb4_unicode_ci` 가 접는 축)의 예시로만 쓰고, 그 축들이 최종적으로 함께 저장된다고 약속하지 않는다.
     - 시나리오 정리: **`가나 표기만 다른 두 추가 정답이 함께 저장된다` 시나리오를 삭제**한다(`normalize-answer-matching` 이 접는 축이라 두 change 이후 거짓이 된다). `탁점만 다른 두 추가 정답이 함께 저장된다`(`ハハ`/`ババ`)·`방 생성 시 스냅샷 복사도 같은 기준을 따른다`·`완전히 같은 값은 하나로만 저장된다` 는 유지한다. `플레이리스트 수정 경로도 같은 기준을 따른다` 는 "표기만 다른 두 추가 정답" → **"탁점만 다른 두 추가 정답"** 으로 바꾼다.
     - 방 생성 스냅샷 시나리오가 앞 시나리오의 가나 예시를 참조하고 있으면 탁점 예시를 참조하도록 함께 고친다.
  2. `openspec/changes/fix-additional-title-collation/tasks.md` 수정
     - `2.2`(가나 조합 `ファイティングマイウェイ`/`ファイティングマイウエイ` 저장 검증)를 **삭제한다.** `normalize-answer-matching` 구현 후 반드시 깨지는 테스트다. 회귀 판별 역할은 `2.1`(탁점 `ハハ`/`ババ`)이 영구히 수행한다 — 탁점은 `unicode_ci` 가 접고 `TitleNormalizer` 는 접지 않으므로 두 change 이후에도 유효하다.
     - `2.3`(방 생성)·`2.5`(수정 경로)가 가나 조합을 쓰고 있으면 탁점 조합으로 바꾼다.
     - 삭제·변경한 자리에 이유를 한 줄로 남긴다(후속 change 가 접는 축이라 영구 회귀 테스트로 쓸 수 없음).
  3. `openspec/changes/fix-additional-title-collation/proposal.md` 점검
     - 표기 축 표는 `unicode_ci` 가 접는 축의 **설명**이므로 그대로 둔다. 다만 본문 어디든 "가나 표기 두 개를 모두 저장할 수 있게 된다"는 취지로 결과를 약속하는 문장이 있으면 저장소 보증 표현으로 고친다.
  4. `openspec/changes/normalize-answer-matching/design.md` 에 **두 change 의 역할 분담을 명시하는 내용을 추가**한다(별도 Decision 절 또는 Context 보강). 담을 것:
     - 선행 change 는 **저장소가 판단하지 않게** 만드는 것이고, 본 change 는 **애플리케이션이 무엇을 같다고 볼지** 정하는 것이다. 두 요구사항은 계층이 아니라 **접는 축의 집합**으로 갈린다.
     - `unicode_ci` 가 접는 축(가나 4축 + 탁점) ⊃ `TitleNormalizer` 가 접는 축(가나 4축). **차집합은 탁점**이고, 그래서 선행 change 의 저장 계층 보증은 본 change 이후에도 탁점 축에서 실제로 필요하다 — 정규화만으로는 `ハハ`/`ババ` 충돌을 막을 수 없다.
     - 이 관계 때문에 두 delta 가 서로 뒤집지 않고 공존한다는 점을 적는다.
  5. `openspec/changes/normalize-answer-matching/proposal.md` 의 Impact 중 "`playlist-track-answer` (ADDED) … 선행 change 가 만든 capability 위에 얹는다" 서술을, 4번의 축 집합 관계로 정확히 다시 쓴다("얹는다"는 계층 비유가 이번 충돌의 원인이었다).
- 상태: 반영완료
- 라운드: 1
