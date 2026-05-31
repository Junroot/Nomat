## REMOVED Requirements

### Requirement: 본 PR(Phase A) 동안 Debezium 경로 유지

**Reason**: Phase A는 무중단 컷오버를 위한 dual-write 안전망 단계였다. Phase A의 dev 운영 검증(미완료 publication 가장 오래된 항목 age < 1분, ES 문서 카운트 ≈ MySQL playlist row 카운트, ShedLock 단일 인스턴스 실행, favorite_playlist 고아 row 0건)이 모두 충족된 시점에서 본 Requirement는 한시적 scoping requirement로서의 역할이 종료된다. Phase B 머지 후 ES 동기화는 `EsPlaylistSyncHandler` 단일 경로로만 수행되며 Debezium·Kafka 의존성은 코드와 인프라에서 모두 제거된다.

**Migration**: 본 변경 적용 후 다음 보장이 유지된다 — 1) 동일 capability의 다른 Requirement(생성·수정 시 ES upsert, 삭제 시 ES 인덱스 삭제, favorite 정리, 도메인 이벤트 기반 동기화 메커니즘)는 그대로 유효하며 단일 경로(Modulith)에서 동작. 2) ES 인덱스 매핑·`PlaylistDocument` 스키마 변경 없음 — 외부 사용자(검색 API 호출자) 영향 없음. 3) 기존 `PlaylistControllerTest.searchByTitle` 등 ES 의존 통합 테스트가 회귀 없이 통과해야 함이 본 변경의 검증 기준에 포함됨. 4) 운영 롤백이 필요하면 본 PR `git revert` + `infra/data` Kafka stack 재배포로 dual-write 상태로 복귀 가능 (자세한 절차는 design.md Decision 5 참조).
