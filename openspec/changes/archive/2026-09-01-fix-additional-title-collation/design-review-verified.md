# 검증 사실 캐시

이 루프 실행 중 실제 코드를 열어 확인한 관찰 사실. 코드는 루프 중 불변이므로
같은 루프의 후속 에이전트는 이 관찰을 직접 확인한 것과 동등하게 신뢰해도 된다.
사실만 담는다 — 심각도·지적·권고·평가 금지.

## 스키마 / 마이그레이션

- `back/src/main/resources/db/migration/` 전체 목록 — `V1`~`V10`만 존재하고 `V11`은 없다. 최신은 `V10__create_event_publication.sql` (라운드 1)
- `V4__create_table_playlist.sql:38-43` — `track_additional_title(track_id BIGINT NOT NULL, additional_title VARCHAR(150) NOT NULL, PRIMARY KEY (track_id, additional_title))`, 테이블 절에 `DEFAULT CHARACTER SET 'utf8mb4' COLLATE 'utf8mb4_unicode_ci'`. 컬럼에 개별 COLLATE 지정 없음 (라운드 1)
- `V9__recreate_table_room.sql:42-48` — `room_track_additional_title(room_id, track_id, additional_title VARCHAR(150) NOT NULL, PRIMARY KEY (room_id, track_id, additional_title))`, 테이블 절에 `COLLATE 'utf8mb4_unicode_ci'`. 컬럼에 개별 COLLATE 지정 없음 (라운드 1)
- `V9__recreate_table_room.sql:29-40` — `room_playlist_track` PK 는 `(room_id, track_id)`. `title VARCHAR(150)` 은 PK·인덱스에 포함되지 않는다 (라운드 1)
- `back/src/main/resources/db/migration/*.sql` 전체 grep `-i "unique|primary key"` — `UNIQUE` 키워드는 어느 마이그레이션에도 없다. 문자열이 PK 에 포함된 테이블은 `track_additional_title`, `room_track_additional_title`, `hibernate_sequences(sequence_name VARCHAR(255) PRIMARY KEY)` 세 개다. `V1:26` 의 `PRIMARY KEY (room_id, player_id, nickname)` 은 `V3:7` 에서 `(room_id, player_id)` 로 대체되고 `V9:1-2` 에서 `room`·`room_entry` 가 DROP 된다 (라운드 1)
- `V4__create_table_playlist.sql` 전체 / `V9__recreate_table_room.sql` 전체 — `FOREIGN KEY` 선언 없음 (라운드 1)

## 엔티티 매핑

- `Track.kt:28-32` — `additionalTitles: Set<String>` 에 `@ElementCollection(fetch = EAGER)`, `@CollectionTable(name = "track_additional_title", joinColumns = [JoinColumn(name = "track_id")])`, `@Column(name = "additional_title")` (라운드 1)
- `Track.kt:52-57` — `MAX_TITLE_LENGTH = 100`, `MAX_ADDITIONAL_TITLE_COUNT = 10` (라운드 1)
- `RoomPlaylistTrack.kt:22-29` — `additionalTitles: Set<String>` 에 `@ElementCollection(fetch = EAGER)`, `@CollectionTable(name = "room_track_additional_title", joinColumns = [JoinColumn("room_id"), JoinColumn("track_id")])`, `@Column(name = "additional_title")` (라운드 1)
- `PlaylistCreationRequest.kt:48-49, 57-61` — `additionalTitles: Set<String>`, `@Size(max = MAX_ADDITIONAL_TITLE_COUNT)`, init 블록에서 각 값의 길이를 `1..Track.MAX_TITLE_LENGTH(=100)` 로 검증. 정규화·소문자화·trim 없음 (라운드 1)

## 애플리케이션 경로

- `RoomService.kt:95-107` — 방 생성 시 `playlist.tracks.map { RoomPlaylistTrack(..., it.additionalTitles, ...) }` 로 그대로 복사 후 `roomPlaylistTrackRepository.save`. 102 행이 `it.additionalTitles` (라운드 1)
- `PlaylistService.kt:43-61` — `update` 는 `trackRepository.deleteByPlaylist(playlist)` 후 `request.tracks.map { it.toDomain(playlist) }` 를 `saveAll` 한다 (라운드 1)
- `AnswerMatcher.kt:9-17` — `normalize` 는 `value.filterNot { it.isWhitespace() }.lowercase()`. `matches` 는 정규화한 입력을 정규화한 정답들과 `==` 비교 (라운드 1)
- `RoundService.kt:73` — `AnswerMatcher.matches(content, track.additionalTitles + track.title)` (라운드 1)
- `front/.../AdditionalTitleEditor.tsx:17-21` — `additionalTitle.trim()` 후 `additionalTitles.includes(trimedAdditionalTitle)` 로 중복 검사. 대소문자·정규화 처리 없음 (라운드 1)

## 부수 효과 전파 경로

- `back/src/main/kotlin` 전체 grep `additionalTitle|additional_title` — 등장 위치는 `PlaylistWithTrackResponse.kt`, `PlaylistCreationRequest.kt`, `Track.kt`, `RoundService.kt:73`, `RoomService.kt:102`, `RoundStartedEventMessage.kt`/`RoundStartedEvent.kt`/`RoundRevealedEvent.kt` 주석, `RoomPlaylistTrack.kt`, `LocalDataSeeder.kt` 뿐이다. 추가 정답을 저장하는 테이블은 `track_additional_title`·`room_track_additional_title` 두 개뿐 (라운드 1)
- `PlaylistDocument.kt:10-20` — ES 인덱스 `playlist-v2` 문서 필드는 `title`, `description`, `id` 세 개뿐. `additionalTitles` 필드 없음 (라운드 1)
- 저장소 전체 grep `-li "debezium"` — 히트는 `CLAUDE.md`, `REVIEW.md`, `openspec/specs/*`, `openspec/changes/archive/*` 문서뿐이고 `back/src` 코드에는 없다. `openspec/changes/archive/2026-05-19-remove-debezium-kafka-phase-b/` 가 존재한다 (라운드 1)
- `back/src/main/kotlin` 전체 grep `@Query|createNativeQuery|nativeQuery` — 5곳(`TrackRepositoryImpl.kt:46`, `RoomRepositoryImpl.kt:43`, `RoomPlaylistTrackRepositoryImpl.kt:51,61,71`). 모두 JPQL 이고 WHERE/JOIN 조건은 `playlist`, `room.id`, `roomId IN`, `representative = true` 같은 식별자·불리언 비교다. 문자열 컬럼 간 비교나 `additional_title` 참조는 없다 (라운드 1)

## 실행 환경

- `ContainerConfiguration.kt:19-25` — `MySQLContainer("mysql:8.0.39")`, `@Profile(["local","test"])` (라운드 1)
- `infra/data/compose.yml:3` — `image: mysql:8.0.39` (라운드 1)
- `back/src/main/resources/application.yml` local|test 절 — `spring.jpa.hibernate.ddl-auto: validate`, `spring.flyway.clean-disabled: false`. dev 절 — `ddl-auto: validate`, flyway 설정 없음(기본값 사용) (라운드 1)
- `back/src/test/kotlin/ilpak/nomat/infrastructure/integration/` — `IntegrationTest.kt`, `IntegrationTestExecutionListener.kt`, `step/PlaylistStep.kt` 존재. `back/src/test/kotlin/ilpak/nomat/playlist/in/PlaylistControllerTest.kt` 존재 (라운드 1)
- `infra/app/compose.yml:2-21` — `spring-app` 은 `replicas: 2`, `update_config: {parallelism: 1, delay: 10s, monitor: 60s, failure_action: rollback, order: start-first}`, healthcheck `curl -f http://localhost:8081/health` (interval 30s, timeout 10s, retries 3, start_period 60s) (라운드 1)

## MySQL 8.0.39 실측 (docker `mysql:8.0.39` 컨테이너에서 직접 실행)

- `information_schema.COLLATIONS` — `utf8mb4_0900_bin` 존재, `PAD_ATTRIBUTE = NO PAD`. `utf8mb4_bin` 은 `PAD SPACE`. `utf8mb4_unicode_ci` 는 `PAD SPACE` (라운드 1)
- `utf8mb4_unicode_ci` 비교 결과 전부 `1`(같음): `'ハ'='バ'`, `'ウェイ'='ウエイ'`, `'まいうぇい'='マイウェイ'`, `'ﾏｲｳｪｲ'='マイウェイ'`, `'Monster'='monster'`, `'ab'='ab '` (라운드 1)
- `utf8mb4_0900_bin` 비교 결과: `'ハ'='バ'` → `0`, `'ab'='ab '` → `0`. `utf8mb4_bin` 에서 `'ab'='ab '` → `1` (라운드 1)
- `V4` 와 동일한 정의로 만든 `track_additional_title` 에 `'ファイティングマイウェイ'` 삽입 후 `'ファイティングマイウエイ'` 삽입 → `ERROR 1062 Duplicate entry '1-ファイティングマイウエイ' for key 'track_additional_title.PRIMARY'` (라운드 1)
- 같은 테이블에 `ALTER TABLE track_additional_title MODIFY COLUMN additional_title VARCHAR(150) NOT NULL COLLATE utf8mb4_0900_bin, ALGORITHM=INPLACE` → `ERROR 1846 ALGORITHM=INPLACE is not supported. Reason: Cannot change column type INPLACE. Try ALGORITHM=COPY.` (라운드 1)
- 같은 ALTER 를 ALGORITHM 지정 없이 실행 → 성공. 기존 행 유지. 이후 `'ファイティングマイウエイ'` 삽입 성공해 두 행 공존. `SHOW CREATE TABLE` 결과 컬럼은 `COLLATE utf8mb4_0900_bin`, 테이블 절은 `COLLATE=utf8mb4_unicode_ci` 로 그대로 남음 (라운드 1)
- 위 두 행이 있는 상태에서 역방향 `ALTER ... MODIFY COLUMN additional_title VARCHAR(150) NOT NULL COLLATE utf8mb4_unicode_ci` → `ERROR 1062 Duplicate entry '1-ファイティングマイウエイ' for key 'track_additional_title.PRIMARY'` (라운드 1)

## 기존 스펙

- `openspec/specs/` 목록 — `playlist-track-answer` 는 없다(본 change 가 신규 추가). `추가 정답|additionalTitle` 을 언급하는 기존 스펙은 `openspec/specs/room-game-session/spec.md` 하나뿐 (라운드 1)
- `openspec/specs/room-game-session/spec.md:118, 121` — 정답 일치는 "입력과 정답 양쪽에서 모든 공백을 제거하고 대소문자를 무시해 비교"로 규정. 저장 유일성 기준에 대한 요구사항은 없다 (라운드 1)
