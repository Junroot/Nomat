-- 추가 정답의 유일성 판정 기준을 코드포인트 일치로 맞춘다.
-- utf8mb4_unicode_ci 는 일본어 표기 흔들림(탁점·가나 종류·전각/반각)을 동일시해
-- 정당한 추가 정답 조합이 PK 위반으로 거부됐다.
-- utf8mb4_0900_bin 은 NO PAD 라 JVM String.equals 와 정확히 일치한다.
-- 두 테이블은 방 생성 시 스냅샷 복사로 이어지므로 반드시 함께 옮긴다.
-- 배포 주의 — 롤백 창은 시간이 지나면 닫힌다:
--   두 컬럼 모두 PK 구성 요소라 이 ALTER 는 ALGORITHM=COPY 로 수행되고,
--   복사 중에는 읽기는 되지만 쓰기가 차단된다. 애플리케이션 재시작은 필요 없다.
--   역방향 ALTER(0900_bin -> unicode_ci)는 bin 상태에서 ci 기준으로 충돌하는 행이
--   하나라도 생기면 실패한다. 되돌리려면 그 행을 지워야 하고 그것은 사용자 데이터 손실이다.
--   즉 롤백은 "아직 아무도 그런 조합을 등록하지 않았을 때"만 가능하다.

ALTER TABLE track_additional_title
    MODIFY COLUMN additional_title VARCHAR(150) NOT NULL COLLATE utf8mb4_0900_bin;

ALTER TABLE room_track_additional_title
    MODIFY COLUMN additional_title VARCHAR(150) NOT NULL COLLATE utf8mb4_0900_bin;
