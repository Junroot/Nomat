## Why

프로덕션에서 플레이리스트 생성이 500으로 실패한다.

```
ERROR [ilpak.nomat.infrastructure.web.GlobalControllerAdvice] Internal server error occurred
org.springframework.dao.DataIntegrityViolationException: could not execute batch
  [Duplicate entry '5030-ファイティングマイウェイ' for key 'track_additional_title.PRIMARY']
  at ilpak.nomat.playlist.in.PlaylistController.save(PlaylistController.kt:35)
```

`Track.additionalTitles`는 `Set<String>`이라 JVM 기준으로 중복이 없고, 프론트도 `AdditionalTitleEditor.tsx:19`에서 문자열 일치로 중복을 걸러 보낸다. 그런데 커밋 시점에 MySQL이 중복이라며 거부한다. **세 관문이 서로 다른 "같음"을 쓰기 때문이다.**

```
        "ファイティングマイウェイ"  vs  "ファイティングマイウエイ"
                          │
      ┌───────────────────┼───────────────────┐
      ▼                   ▼                   ▼
  ┌─────────┐        ┌─────────┐        ┌──────────┐
  │ 프론트   │        │  JVM    │        │  MySQL   │
  │ 문자열   │        │Set<Str> │        │unicode_ci│
  │ 일치     │        │  ==     │        │ primary  │
  └─────────┘        └─────────┘        └──────────┘
    다름(통과)         다름(통과)          ★같음★ → PK 위반, 500
```

`track_additional_title`(`V4__create_table_playlist.sql:43`)과 `room_track_additional_title`(`V9__recreate_table_room.sql:48`)은 `utf8mb4_unicode_ci`다. 이 콜레이션은 UCA primary weight만 비교하므로 일본어에서 다음이 **전부 같은 값**이 된다.

| 축 | 예 |
|---|---|
| 히라가나 ↔ 가타카나 | `まいうぇい` = `マイウェイ` |
| 작은 가나 ↔ 큰 가나 | `ウェイ` = `ウエイ` |
| 전각 ↔ 반각 | `ﾏｲｳｪｲ` = `マイウェイ` |
| 탁점·반탁점 | `ハ` = `バ` = `パ` |

**이 제약은 오타를 막는 안전장치가 아니라 기능의 목적을 정면으로 거스른다.** 추가 정답은 "표기가 흔들려도 정답으로 인정받게" 하려고 등록하는 값이다. 그런데 현재 `AnswerMatcher`는 공백·대소문자만 무시하는 정확 비교라, 플레이어가 `ウエイ`라고 입력하면 정답 `ウェイ`와 매칭되지 않는다. 즉 사용자는 두 표기를 **모두 등록해야 하는데, DB가 바로 그 조합만 골라서 거부한다.**

본 변경이 보장하는 것은 여기까지다 — **저장소가 표기 차이를 이유로 저장을 거부하지 않는다.** 어떤 표기 축을 같은 정답으로 볼지는 애플리케이션의 몫이고, 후속 change `normalize-answer-matching`이 가나 축을 접으면 그 축은 등록 자체가 불필요해진다. 본 변경은 특정 두 표기가 최종적으로 함께 저장된다고 약속하지 않는다.

한국어·영어 플레이리스트에서는 걸리지 않고 일본곡에서만 터지는 이유가 이것이다.

## What Changes

두 컬럼의 콜레이션을 `utf8mb4_0900_bin`으로 바꾼다. 그러면 DB의 유일성 기준이 JVM `Set<String>`과 일치한다.

```
  변경 전:  앱 [코드포인트 일치] ─────────── DB [표기 흔들림도 같음]
            엄격                              관용        ← 방향이 반대. 500 발생

  변경 후:  앱 [코드포인트 일치] · DB [코드포인트 일치]
            판단 기준 일치. DB는 의견을 갖지 않는다.
```

- `track_additional_title.additional_title`
- `room_track_additional_title.additional_title`

**두 테이블은 반드시 함께 옮긴다.** `RoomService.kt:102`가 방 생성 시 추가 정답을 그대로 스냅샷 복사하므로, `track` 쪽만 바꾸면 플레이리스트 저장은 성공하는데 **방 생성에서 500이 나는 더 나쁜 상태**가 된다.

애플리케이션 코드는 변경하지 않는다. 정답 판정은 DB가 아니라 `AnswerMatcher`가 애플리케이션에서 수행하므로, 이 컬럼의 `_ci` 콜레이션은 얻는 것 없이 PK의 의미만 왜곡시키고 있었다. ES 색인(`playlist-search-sync`)은 추가 정답을 다루지 않아 무관하다.

**표기 흔들림을 사용자가 일일이 등록하지 않아도 되게 만드는 일은 본 변경의 범위가 아니다.** 후속 change `normalize-answer-matching`이 담당한다. 본 변경은 장애를 멈추고, 그 후속 변경이 설 수 있는 저장 기반을 만든다. 두 change는 독립적으로 배포 가능하며 본 변경이 선행이다.

## Impact

- **영향 스펙**: `playlist-track-answer` (신규 capability, ADDED) — 추가 정답 저장의 유일성 기준을 명시한다. 지금까지 어느 스펙도 이 기준을 다루지 않아 콜레이션이라는 우연한 선택이 사실상의 규칙 노릇을 해 왔다
- **영향 서브프로젝트**: `back/` 만. `front/`·`infra/` 변경 없음
- **영향 도메인 모듈**: `playlist`, `room` — 다만 **Kotlin 코드는 변경하지 않는다.** 헥사고날 계층(in/out/application) 어디에도 변경이 없다
- **DB 스키마 영향** (별도 항목):
  - Flyway `V11__change_additional_title_collation.sql` 신규
  - `track_additional_title.additional_title`: `utf8mb4_unicode_ci` → `utf8mb4_0900_bin`
  - `room_track_additional_title.additional_title`: 동일
  - 두 컬럼 모두 PK 구성 요소라 **PK 인덱스 재구축을 동반**한다 (`ALGORITHM=COPY`)
  - **선행 데이터 정리 불필요** — 근거는 `design.md` Decision 2
  - **롤백 가능 창이 제한적**이다 — 근거는 `design.md` Decision 3
- **ES 매핑·Kafka 토픽·Redis 키 영향**: 없음
- **후속 의존**: `normalize-answer-matching`이 본 변경을 선행 조건으로 삼는다
