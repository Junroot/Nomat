# design.md 적대적 리뷰

검증 통과 지적 1건(치명 1, 높음 0).

### [치명] 선행 change가 세운 스펙 요구사항을 정면으로 뒤집는데 delta 가 ADDED 뿐이다

- **위치**
  - `design.md` Context(11~24행): "관련해서 `fix-additional-title-collation`이 **저장 계층**의 동치 기준을 코드포인트 일치로 못 박았다 … `DB 는 왼쪽 끝에 고정 — 판단하지 않는다`"
  - `design.md` Decision 4(101~125행) — 매칭 규칙 = 중복 판정 규칙
  - `specs/playlist-track-answer/spec.md:1-35` — `## ADDED Requirements` + "추가 정답은 표기 정규화 기준으로 중복 등록되지 않는다"
  - `proposal.md:80` — "`playlist-track-answer` (ADDED) … `fix-additional-title-collation`이 만든 capability **위에 얹는다**"
  - 반대편: `openspec/changes/fix-additional-title-collation/specs/playlist-track-answer/spec.md:3-25`

- **설계 주장**: 선행 change 가 정한 것은 **저장 계층(DB 콜레이션)의 동치 기준**이고, 본 change 는 그 위에 애플리케이션 판단을 "얹는" 관계다. 그래서 선행 요구사항을 건드릴 필요가 없고 delta 는 ADDED 로 충분하다.

- **무엇이 깨지나**: 선행 change 의 요구사항은 DB 콜레이션이 아니라 **API 로 관측되는 시스템 행위**로 쓰여 있다.

  | | `fix-additional-title-collation` (선행) | `normalize-answer-matching` (본 change) |
  |---|---|---|
  | 요구사항 | "다음 축에서만 다른 두 값은 **서로 다른 추가 정답**이며 한 트랙에 **함께 저장될 수 있어야(SHALL)** 한다" — 축에 히라가나↔가타카나·큰가나↔작은가나·전각↔반각·대소문자 포함 | "표기 정규화 후 같은 값이 되는 두 항목을 **함께 보관하지 않아야(SHALL NOT)** 한다" — 접는 축이 위와 동일 |
  | 시나리오 | "가나 표기만 다른 두 추가 정답이 함께 저장된다" — `ファイティングマイウェイ`+`ファイティングマイウエイ` → **두 값 모두 저장**, 요청 성공 | "가나 표기만 다른 추가 정답은 하나만 보관된다" — `マイウェイ`+`まいうぇい` → **하나만 저장** |

  두 요구사항은 같은 capability(`playlist-track-answer`)에 **둘 다 ADDED** 로 들어간다. 두 change 가 아카이브되면 그 capability 는 같은 입력에 대해 "둘 다 저장해야 한다"와 "하나만 보관해야 한다"를 동시에 SHALL 로 요구하는 자기모순 상태가 된다. 선행 change 의 5개 축 중 본 change 가 접지 않는 것은 탁점 하나뿐이고 나머지 4개 축이 전부 뒤집힌다.

  가정이 아니라 확정 경로다. `tasks.md:3`(0.1)이 선행 change 의 **배포 완료를 하드 선행조건**으로 못 박고 있고, 선행 change 의 `tasks.md:12`(2.2)는 `ファイティングマイウェイ`/`ファイティングマイウエイ`가 **둘 다 저장되는지** 검증하는 통합 테스트를 만들도록 요구한다. 본 change 의 `tasks.md:18`(2.5)은 "표기만 다른 두 추가 정답을 담아 요청하면 **하나만 저장**되는지" 검증하는 테스트를 만들도록 요구한다. 순서대로 구현하면 `./gradlew test`(본 change 2.7)에서 선행 change 의 2.2 테스트가 반드시 깨진다.

  이건 구현자가 코드를 보고 국소적으로 정할 수 있는 사항이 아니다. 선행 요구사항을 **저장 계층 한정으로 좁혀 MODIFIED** 할 것인지, **REMOVED** 할 것인지(그리고 좁힌다면 "코드포인트 유일성"이라는 선행 요구사항이 무엇을 근거로 계속 필요한지 — 탁점 축만 남는가)는 스펙 계약 자체의 결정이며, 설계가 정하지 않으면 두 스펙 중 어느 쪽이 진실인지 아무도 판정할 수 없다. `design.md` 는 이 충돌을 인지조차 하지 않고 "DB 는 판단하지 않는다"는 계층 구분으로 넘어가는데, 선행 스펙의 문장은 DB 가 아니라 시스템의 저장 결과를 규정한다.

- **검증 근거**
  - `openspec/changes/fix-additional-title-collation/specs/playlist-track-answer/spec.md:5`("서로 다른 추가 정답이며 한 트랙에 함께 저장될 수 있어야 한다"), `:9-13`(축 목록), `:23-25`(가나 시나리오 — "두 값이 모두 저장되고 요청이 성공해야 한다")
  - `openspec/changes/normalize-answer-matching/specs/playlist-track-answer/spec.md:5`, `:16-19`(가나 시나리오 — "먼저 온 `マイウェイ`만 저장되어야 한다")
  - 양쪽 delta 파일 1행이 모두 `## ADDED Requirements` — MODIFIED/REMOVED 없음
  - `openspec/changes/fix-additional-title-collation/tasks.md:12` vs `openspec/changes/normalize-answer-matching/tasks.md:18`
  - `openspec/specs/` 에 `playlist-track-answer` 없음 — 선행 change 미아카이브 상태이므로 두 delta 는 아카이브 시 같은 capability 파일로 합쳐진다
  - `PlaylistController.kt:29-45` — save/update 가 같은 `PlaylistCreationRequest` 를 받으므로 두 스펙의 충돌은 생성·수정 양쪽 경로에 동일하게 걸린다

## 기각한 후보

- **"수정(PUT) 경로가 설계에서 빠졌다"** — 반증됨. `PlaylistController.kt:38-45` 의 `update` 가 `save` 와 **동일한** `PlaylistCreationRequest` 를 받고, `PlaylistService.kt:54-56` 이 `deleteByPlaylist` 후 `toDomain()` 으로 재삽입한다. `PlaylistCreationRequestTrack` 한 곳만 고치면 Decision 6 의 레거시 편집 시나리오까지 그대로 커버된다.
- **"추가 정답 10개 상한(@Size) 검증이 접기 전에 걸려 레거시 편집이 400 으로 막힌다"** — 반증됨. `Track.kt:56` 의 상한이 10 이고 레거시 플레이리스트도 최대 10개까지만 저장돼 있으므로, 접기 전 개수가 상한을 넘는 요청이 성립하지 않는다.
- **"부수 효과 전파 누락 — ES 색인"** — 반증됨. `EsPlaylistSyncHandler.kt:15-29` 는 `PlaylistDocument(title, description, id)` 만 저장하고 추가 정답·트랙 제목을 색인하지 않는다. 설계의 Non-Goal(ES 분석기 제외)이 실제 코드와 일치한다.
- **"`common/` 배치가 모듈 경계 검증을 깬다"** — 반증됨. `back/src` 전체 grep 결과 `ApplicationModules` 구조 검증 테스트가 없고, `PlaylistCreationRequest.kt:3` 이 이미 `ilpak.nomat.common.exception` 을 모듈 간 참조하고 있다.
- **"Decision 3 의 비용 주장이 과소평가"** — 반증됨. `RoundService.kt:71-73` 이 채팅 1건마다 `roomPlaylistTrackRepository.findByRoomId` 를 이미 호출하고 매칭 대상은 `additionalTitles + title` 로 최대 11개다. 설계의 서술과 코드가 일치한다.
- **"`room-game-session` MODIFIED 가 기존 시나리오를 유실"** — 반증됨. `openspec/specs/room-game-session/spec.md:116-136` 의 기존 4개 시나리오가 delta 에 모두 보존되어 있고 새 시나리오만 추가됐다.

판정: 조건부(치명·높음 1건 선해결) — `fix-additional-title-collation` 의 `playlist-track-answer` 요구사항을 본 change 가 뒤집는다는 사실을 설계가 인정하고, 그 요구사항을 어떻게 처리할지(저장 계층 한정으로 MODIFIED 인지 REMOVED 인지)를 결정해 delta 와 tasks(선행 change 의 테스트 2.2 포함)에 반영해야 진입 가능하다.
