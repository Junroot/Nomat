## Why

현재 정답 판정(`AnswerMatcher`)은 **모든 공백을 제거하고 대소문자를 무시**하는 것이 전부다. 그래서 같은 곡을 가리키는 표기 흔들림이 전부 오답이 된다.

```
   정답: "ファイティングマイウェイ"

   플레이어 입력                      판정      실제로는
   ─────────────────────────────      ────      ──────────
   ファイティングマイウエイ            오답 ✗    같은 곡
   ふぁいてぃんぐまいうぇい            오답 ✗    같은 곡
   ﾌｧｲﾃｨﾝｸﾞﾏｲｳｪｲ                      오답 ✗    같은 곡
   ファイティング・マイ・ウェイ         오답 ✗    같은 곡
```

지금은 이 흔들림을 **사용자가 추가 정답으로 일일이 등록해서** 메워야 한다. 트랙당 추가 정답은 10개(`Track.MAX_ADDITIONAL_TITLE_COUNT`)로 제한돼 있는데, 가나 표기 조합만으로 그 예산이 소진된다. 게다가 노래 맞히기는 **먼저 맞히는 사람이 이기는** 게임이라, 표기를 잘못 고른 플레이어는 정답을 알고도 진다.

한국어에서도 같은 일이 벌어진다.

```
   정답: "밤을 달리다!"          입력: "밤을 달리다"     → 오답 ✗
   정답: "Don't Stop"           입력: "dont stop"       → 오답 ✗
   정답: "夏〜Summer〜"          입력: "夏Summer"        → 오답 ✗
```

## What Changes

정답 비교에 쓰는 정규화 규칙을 도입한다. 규칙의 기준은 하나다.

```
   접는다 — 같은 내용을 다르게 "표기"한 것 (정보량 동일)
      マイ・ウェイ = まいうぇい = ﾏｲｳｪｲ = マイウエイ
      Monster! = monster
      밤을 달리다 = 밤을달리다

   접지 않는다 — 내용 자체가 다른 것 (정보량 차이)
      Monster        ≠  Monster (feat. Rihanna)     ← 괄호 안은 정보다
      ハハ           ≠  ババ                          ← 탁점은 정보다
      メール         ≠  メル                          ← 장음은 정보다
```

이 원칙은 규칙 목록이 아니라 판단 기준이다. 새로운 표기 축이 나와도 "정보를 버리는가"로 판정할 수 있다.

### 정규화 파이프라인 (`common/TitleNormalizer`)

```
   normalize(s):
      s
       ├─▶ NFKC                        전각/반각·합성 통일
       ├─▶ lowercase(ROOT)
       ├─▶ 히라가나 → 가타카나           U+3041‥U+3096 + 0x60
       ├─▶ 작은 가나 → 큰 가나           ァィゥェォッャュョヮヵヶ → アイウエオツヤユヨワカケ
       └─▶ [^\p{L}\p{N}] 전부 제거      공백·구두점·기호·괄호문자·이모지
              └─ 결과가 비면 ▶ 공백만 제거한 값으로 폴백
```

마지막 단계가 종전의 "공백 제거" 규칙을 **포함하면서 확장**한다. 공백도 구두점도 기호도 괄호도 전부 "문자·숫자가 아닌 것"이므로 규칙 하나로 정의된다. 이 카테고리 기반 규칙은 일본어에서 정확히 원하는 대로 갈라진다 — `・`(Po)·`〜`(Pd)는 제거되고 `ー`(장음, Lm)·`々`(Lm)는 보존된다. 예외를 손으로 열거할 필요가 없다.

### 같은 규칙을 중복 판정에도 쓴다

두 추가 정답이 매칭상 구분 불가하면 둘 다 저장할 이유가 없다(순수 노이즈). 따라서 **매칭 규칙과 중복 판정 규칙은 같은 것 하나**를 쓴다. 근거는 `design.md` Decision 4.

- **프론트**: 같은 규칙으로 중복 추가를 **사전에 차단**하고, 왜 막혔는지 알린다. `マイウェイ` 등록 후 `マイ・ウェイ`가 거부되면 사용자 눈에는 다른 글자라 이유를 모른다
- **백엔드**: 공개 API이므로 백스톱으로 **조용히 접는다**. 거부하지 않는 이유는 `design.md` Decision 6

### 곁다리 결함 수정

`front/app/components/ui/AdditionalTitleEditor.tsx:26`에서 괄호가 빠져 길이 상한 검사가 죽어 있다.

```ts
return additionalTitle.trim().length > 0 && additionalTitle.trim.length < 50
                                                            ↑ 함수의 arity(0)를 비교 중
```

`0 < 50`이 항상 참이라 상한이 검사되지 않고, 상수도 `maxTitleLength`(100)·`Track.MAX_TITLE_LENGTH`(100)와 어긋난다. 실질 상한은 `<input maxLength>`가 우연히 지켜주고 있다. 중복 차단 로직을 넣으며 같은 함수를 건드리므로 함께 고친다.

## Impact

- **영향 스펙**
  - `playlist-track-answer` (ADDED) — 추가 정답 등록 시의 중복 판정 기준과 원문 보존 요구사항. 같은 capability에 `fix-additional-title-collation`이 넣는 요구사항은 **저장소가 표기 차이를 이유로 저장을 거부하지 않는다**는 저장 계층 보증이고, 본 change의 요구사항은 **애플리케이션이 어떤 축을 같은 정답으로 접는가**를 정한다. 두 요구사항은 접는 축의 집합으로 갈린다 — `utf8mb4_unicode_ci`가 접는 축(가나 4축 + 탁점) ⊃ `TitleNormalizer`가 접는 축(가나 4축)이고, **차집합인 탁점 축에서 선행 요구사항이 본 change 이후에도 필요**하다. 그래서 두 delta는 서로 뒤집지 않고 `ADDED`로 공존한다 (`design.md` "선행 change 와의 역할 분담")
  - 이 관계를 성립시키기 위해 선행 change의 delta도 함께 조정한다 — 요구사항 본문을 저장 계층 보증으로 재진술하고, 본 change가 접는 가나 축을 "함께 저장된다"고 단언하던 시나리오와 그 테스트 태스크를 제거한다
  - `room-game-session` (MODIFIED) — "라운드는 첫 정답 또는 클립 소진으로 종료된다"가 정답 일치 규칙을 "모든 공백 제거 + 대소문자 무시"로 명시하고 있어 재진술이 필요하다
- **영향 서브프로젝트**: `back/`, `front/`. `infra/` 변경 없음
- **영향 코드 (`back/`)** — 헥사고날 계층별
  - `common/normalize/TitleNormalizer.kt` (신규) — 두 도메인 모듈이 공유하는 순수 규칙. 인바운드/아웃바운드 어댑터 변경은 없다
  - `room/application/domain/AnswerMatcher.kt` — 자체 `normalize`를 `TitleNormalizer` 위임으로 교체. 규칙을 설명하는 KDoc도 갱신
  - `playlist/application/dto/PlaylistCreationRequest.kt` — `PlaylistCreationRequestTrack`이 정규화 키 기준으로 중복을 접는다. `additionalTitles`의 타입을 `Set<String>` → `List<String>`으로 바꾼다 — Jackson이 `Set`을 `HashSet`으로 역직렬화해 입력 순서를 지우므로 "먼저 온 값을 남긴다"가 성립하지 않는다 (`design.md` Decision 8). JSON 계약은 그대로다
- **영향 코드 (`front/`)**: `app/utils/titleNormalizer.ts`(신규), `app/components/ui/AdditionalTitleEditor.tsx`
- **DB 스키마·ES 매핑·Kafka 토픽·Redis 키 영향**: 없음. 저장 형태는 그대로이고 정규화는 비교 시점에만 적용한다 (`design.md` Decision 3)
- **선행 조건**: `fix-additional-title-collation`이 먼저 배포되어야 한다. 탁점은 접지 않기로 했으므로 `ハハ`/`ババ` 조합은 정규화로도 걸러지지 않고 `utf8mb4_unicode_ci`에서 여전히 충돌한다 — 정규화는 콜레이션 수정을 대체하지 못한다
- **배포 중 규칙 변경**: 진행 중인 방의 정답 판정 기준이 배포 시점에 바뀌지만, **관용도가 넓어지는 방향으로만** 바뀌어 종전에 정답이던 입력이 오답이 되는 일은 없다. 허용하기로 결정했다
