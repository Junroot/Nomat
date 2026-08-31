# design.md 적대적 리뷰

**검증 통과 지적 없음** — `[치명]`·`[높음]`으로 세울 수 있는 결함을 찾지 못했다.

설계의 핵심 주장은 전부 실제 코드와 MySQL 8.0.39 실측으로 확인했다. 특히 되돌리기 비싼 선택(콜레이션 변경, PK 재구축, 롤백 비대칭) 세 가지를 `mysql:8.0.39` 컨테이너에서 직접 재현해 검증했고, 설계가 예측한 결과와 일치했다.

## 기각한 후보

아래는 적대적으로 의심했으나 **반증된** 항목이다. 지적이 아니다.

### `utf8mb4_0900_bin` 가용성·PAD 속성 주장이 틀렸을 가능성

design.md Decision 1 말미는 "`utf8mb4_bin`은 PAD SPACE라 후행 공백을 무시해 JVM `String.equals`와 어긋난다. 후자(`utf8mb4_0900_bin`)는 NO PAD여서 정확히 일치한다. MySQL 8.0.39를 쓰고 있어 사용 가능하다"고 단정한다. `utf8mb4_0900_bin`은 MySQL 8.0.17에서 추가된 비교적 늦은 콜레이션이라 버전·속성 주장이 어긋날 여지를 의심했다.

**반증**: `mysql:8.0.39` 컨테이너에서 `information_schema.COLLATIONS` 조회 결과 `utf8mb4_0900_bin`이 존재하며 `PAD_ATTRIBUTE = NO PAD`, `utf8mb4_bin`은 `PAD SPACE`다. 실측 비교에서도 `('ab' COLLATE utf8mb4_bin) = 'ab '` → `1`, `('ab' COLLATE utf8mb4_0900_bin) = 'ab '` → `0`. 버전 주장의 근거인 `ContainerConfiguration.kt:20`(`MySQLContainer("mysql:8.0.39")`)과 `infra/data/compose.yml:3`(`image: mysql:8.0.39`)도 인용 그대로다. 설계 주장이 정확하다.

### Decision 2("선행 데이터 정리 불필요")의 포함관계 논증에 반례가 있을 가능성

"현재 PK가 성립 ⟹ ci에서 서로 다름 ⟹ bin에서도 서로 다름"이라는 논증에 구멍이 있는지 — 즉 `unicode_ci`는 구별하는데 `0900_bin`은 같다고 보는 값 쌍이 존재하는지 의심했다. 그런 쌍이 하나라도 있으면 ALTER가 PK 위반으로 실패해 배포가 깨진다.

**반증**: `0900_bin`은 utf8mb4 바이트열 완전 일치일 때만 같다고 판정하므로, bin에서 같은 두 값은 어떤 콜레이션에서도 같다. 논리적으로 반례가 존재할 수 없다. 실측으로도 `V4`와 동일한 정의의 테이블에 `unicode_ci` 기준 공존 가능한 행들을 넣은 상태에서 정방향 ALTER가 성공하며 기존 행이 그대로 유지됨을 확인했다. 반대로 Decision 3이 예고한 역방향 ALTER는 실제로 `ERROR 1062 Duplicate entry '1-ファイティングマイウエイ'`로 거부됐다 — 설계가 서술한 롤백 비대칭이 그대로 재현된다.

### 무중단 배포 영향 서술(`ALGORITHM=COPY`)이 과장·오기일 가능성

Decision 3은 "MySQL 8.0에서 컬럼 콜레이션 변경은 INPLACE로 처리되지 않아 테이블 복사(`ALGORITHM=COPY`)로 수행된다"고 단정한다. 이 단정이 틀렸다면(실제로는 INPLACE 가능) 배포 계획(tasks 1.1의 행 수 확인)이 불필요하게 무거워지고, 반대로 근거 없는 낙관이면 장애가 된다.

**반증**: 실측 결과 `ALTER ... MODIFY COLUMN additional_title VARCHAR(150) NOT NULL COLLATE utf8mb4_0900_bin, ALGORITHM=INPLACE` 는 `ERROR 1846 ALGORITHM=INPLACE is not supported. Reason: Cannot change column type INPLACE. Try ALGORITHM=COPY.` 로 거부됐다. 설계 주장이 정확하다.

### 부수 효과 전파 경로(ES 색인·Kafka·CDC) 누락

proposal.md는 "ES 색인(`playlist-search-sync`)은 추가 정답을 다루지 않아 무관하다", "ES 매핑·Kafka 토픽·Redis 키 영향: 없음"이라 단정한다. 이 저장소는 CDC로 MySQL 변경을 Kafka·ES로 전파하는 구조가 있어(`CLAUDE.md`), `track_additional_title` 을 `ALGORITHM=COPY` 로 재구축하면 DDL·행 이벤트가 파이프라인으로 새어 나갈 경로를 의심했다.

**반증**: (a) `PlaylistDocument.kt:10-20` 의 ES 문서 필드는 `title`·`description`·`id` 뿐으로 추가 정답을 색인하지 않는다. (b) 저장소 전체 `-li "debezium"` grep 결과 코드 히트가 없고 `openspec/changes/archive/2026-05-19-remove-debezium-kafka-phase-b/` 가 존재한다 — Debezium CDC는 이미 제거됐고 동기화는 Modulith 아웃박스(`EsPlaylistSyncHandler.kt`)로 대체됐다. `CLAUDE.md` 의 CDC 서술이 낡은 것이지 설계의 누락이 아니다. (c) 추가 정답을 저장하는 테이블은 grep 전수 확인 결과 `track_additional_title`·`room_track_additional_title` 두 개뿐이고, 설계는 둘 다 옮긴다.

### 콜레이션 혼재로 인한 `Illegal mix of collations` 런타임 오류

두 컬럼만 `utf8mb4_0900_bin`으로 바꾸고 나머지 테이블·컬럼은 `utf8mb4_unicode_ci`로 남기면(tasks 1.3), 두 콜레이션이 같은 식에서 만나는 순간 MySQL이 `ERROR 1267 Illegal mix of collations`로 쿼리를 거부한다. 설계는 이 위험을 언급하지 않는다.

**반증**: `additional_title` 이 다른 문자열 컬럼과 비교·조인·UNION 되는 지점이 없다. `back/src/main/kotlin` 전체 `@Query|nativeQuery` grep 결과 5곳뿐이고 모두 JPQL 이며 조건은 식별자·불리언 비교(`playlist`, `room.id`, `roomId IN`, `representative = true`)다. 두 컬렉션 테이블은 BIGINT 조인 컬럼으로만 소유 엔티티와 연결된다(`Track.kt:30`, `RoomPlaylistTrack.kt:24-27`). 파라미터 바인딩 비교에서는 컬럼 콜레이션이 coercibility 우선순위로 이기므로 혼재 오류가 나지 않는다. 마이그레이션 자체도 실측에서 테이블 기본 콜레이션을 `utf8mb4_unicode_ci`로 남긴 채 컬럼만 바뀌어 정상 동작했다.

### 검증 계획이 마이그레이션을 실제로 통과하지 않을 가능성

design.md "검증 방법"과 tasks 2절은 통합 테스트로 회귀를 잡겠다고 한다. 테스트가 Flyway를 거치지 않고 Hibernate 자동 DDL로 스키마를 만들면 콜레이션은 서버 기본값(`utf8mb4_0900_ai_ci`)이 되어, 마이그레이션 적용 여부와 무관하게 테스트가 통과해 버린다 — 그러면 tasks 2.1이 주장하는 "마이그레이션이 실제로 적용됐는지를 판별하는 회귀 테스트"가 성립하지 않는다.

**반증**: `application.yml` 의 local|test 절이 `spring.jpa.hibernate.ddl-auto: validate` 라 Hibernate 는 스키마를 만들지 않고, 스키마는 Flyway 마이그레이션이 만든다. 테스트 컨테이너도 `ContainerConfiguration.kt:20` 에서 프로덕션과 같은 `mysql:8.0.39` 다. 따라서 테스트는 `V11` 적용 후의 실제 콜레이션 위에서 돈다. `IntegrationTest.kt`·`step/PlaylistStep.kt`·`PlaylistControllerTest.kt` 도 설계가 인용한 대로 존재한다.

판정: 진입 가능 — 설계의 코드·DB 주장을 전부 앵커로 확인했고, 되돌리기 비싼 세 결정(콜레이션 선택, 선행 정리 불필요, 롤백 비대칭)은 `mysql:8.0.39` 실측으로 설계가 예측한 결과와 일치함을 재현했다. 진입을 막는 치명·높음 결함 없음.
