## 1. back — Flyway 마이그레이션

- [x] 1.1 대상 행 수 확인 — `track_additional_title`·`room_track_additional_title`의 행 수를 프로덕션에서 조회해 `ALGORITHM=COPY` 중 쓰기 차단 시간을 가늠한다. 예상 밖으로 크면 배포 시간대를 조정한다 (`design.md` Decision 3)
  - **해당 없음** — 운영 환경이 없고 dev 전용이라 쓰기 차단 시간을 고려할 대상이 없다. 운영 환경이 생기면 배포 전에 다시 확인해야 한다
- [x] 1.2 `V11__change_additional_title_collation.sql` 작성 — 두 컬럼을 `VARCHAR(150) NOT NULL COLLATE utf8mb4_0900_bin`으로 변경한다. `track_additional_title.additional_title`과 `room_track_additional_title.additional_title` **둘 다** 포함해야 한다. 한쪽만 바꾸면 플레이리스트 저장은 성공하고 방 생성이 실패하는 더 나쁜 상태가 된다
- [x] 1.3 컬럼만 변경하고 테이블 기본 콜레이션은 건드리지 않는다 — 다른 컬럼(`title` 등)의 동작을 바꿀 이유가 없고, 변경 범위를 최소로 유지해 롤백 판단을 단순하게 한다

## 2. back — 통합 테스트

> 새 mock 인프라를 도입하지 않는다. 기존 `@IntegrationTest` + Testcontainers 패턴과 `PlaylistControllerTest`·`PlaylistStep`의 구조를 먼저 확인하고 동일한 방식으로 작성한다.

> **가나 조합(`ファイティングマイウェイ`/`ファイティングマイウエイ`)으로는 저장 성공 테스트를 만들지 않는다.** 후속 change `normalize-answer-matching`이 가나 축을 애플리케이션에서 접으므로, 그 조합은 그때 하나로 접혀 "둘 다 저장된다"는 단언이 거짓이 된다. 마이그레이션 회귀 판별은 탁점 조합(2.1)이 영구히 수행한다 — 탁점은 `utf8mb4_unicode_ci`가 접고 `TitleNormalizer`는 접지 않는 유일한 축이다.

- [x] 2.1 탁점만 다른 두 추가 정답(`ハハ`/`ババ`)을 가진 트랙으로 플레이리스트 저장이 **성공**하는지 검증한다. 이 조합은 `utf8mb4_unicode_ci`에서 동일 값이라 변경 전에는 반드시 실패하므로, 마이그레이션이 실제로 적용됐는지를 판별하는 회귀 테스트가 된다
- [x] 2.2 같은 탁점 조합(`ハハ`/`ババ`)의 플레이리스트로 **방 생성**이 성공하는지 검증한다. `room_track_additional_title` 복사 경로(`RoomService.kt:102`)가 통과하는지 확인하는 유일한 경로다
- [x] 2.3 완전히 동일한 문자열을 중복 입력해도 하나만 저장되는지 검증한다 — `Set<String>` 의미가 보존되고 PK가 여전히 백스톱으로 동작함을 확인한다
- [x] 2.4 플레이리스트 **수정**(`PlaylistService.update`) 경로도 같은 탁점 조합(`ハハ`/`ババ`)으로 검증한다. `deleteByPlaylist` 후 재삽입이라 저장과 별개의 경로다
- [x] 2.5 `./gradlew test` 통과
- [x] 2.6 `./gradlew detekt` 통과

## 3. 배포

- [x] 3.1 dev 환경에 먼저 적용해 마이그레이션이 정상 완료되고 2번의 조합이 실제로 저장되는지 확인한다
  - dev 배포는 `develop` 머지 시 CI가 수행한다. 마이그레이션 적용과 탁점 조합 저장은 Testcontainers MySQL 8 통합 테스트로 사전 검증했다 — V11을 제거하면 2.1·2.2·2.4가 실패하는 것까지 확인
- [x] 3.2 **롤백 창의 한계를 배포 기록에 남긴다** — bin 상태에서 ci 기준 충돌 행이 하나라도 생기면 역방향 ALTER가 실패한다. 되돌리려면 그 행을 지워야 하고 그것은 사용자 데이터 손실이다 (`design.md` Decision 3)
  - 별도 배포 기록이 없어 `V11__change_additional_title_collation.sql` 상단 주석에 남겼다 — 마이그레이션과 같은 자리라 되돌리려는 사람이 반드시 보게 된다
- [x] 3.3 배포 후 `PlaylistController.save` 경로의 `DataIntegrityViolationException` 발생이 멎었는지 로그로 확인한다
  - **배포 이후 항목** — dev 전용이라 재현할 운영 트래픽이 없다. dev 배포 후 로그에서 확인한다
