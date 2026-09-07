# design-review — room-scoped-chat-nickname-colors (라운드 3)

검토 범위: `design.md`, `proposal.md`, `tasks.md`, `specs/room-chat-nickname-color/spec.md`. 코드 주장은 `design-review-verified.md` 캐시(라운드 1·2)와 이번 라운드에 직접 연 파일(`ChatMessage.ts`, tailwind `dist/lib.js`)로 검증했다.

검증 통과 지적 없음. 라운드 2의 유일한 지적(스펙 SHALL "슬롯 0~9 서로 36° 이상"이 hex 표 실측 35.74°에 미달)은 스펙·설계·proposal 모두 "격자 36° / hex 계약 35° 이상, 슬롯 10~19 이웃 17° 이상"으로 통일돼 해소됐고, 표는 라운드 2 표와 동일하므로 실측(0~9 최소 35.74°, 전체 최소 17.22°, 대비 최소 8.85:1)이 새 계약을 만족한다.

## 기각한 후보

- **라운드 2 지적 재발 여부 — 문서 간 임계값 불일치** — 반증. `spec.md:62`(SHALL 35° / 17°), `spec.md:68`(시나리오 35°), `design.md:28`(Goals "36° 격자만큼(hex 양자화 후 실측 35° 이상)"), `design.md:99`(계약 35°/17°), `design.md:120`(실측 35.7°/17.2°), `design.md:135`(Risks), `proposal.md:23`("36° 격자만큼(hex 실측 35° 이상)")이 전부 같은 수치를 쓴다. hex 표 20개는 라운드 2 표와 동일(캐시 라운드 3 항목)하므로 라운드 2 실측이 그대로 유효하고, 최소 35.74° ≥ 35°, 17.22° ≥ 17°, 대비 8.85:1 ≥ 7:1 로 SHALL 전부 만족.
- **"평균 8명 방은 슬롯 0~7만 쓴다"(Goals) 및 스펙 시나리오 "10명 이하 → 서로 35° 이상"이 퇴장·재사용이 반복된 방에서 깨진다는 의심** — 반증. 가장 낮은 빈 슬롯 배정 + `LEFT` 시 해제(Decision 2) 하에서 점유 슬롯 수 k 일 때 새 배정은 항상 ≤ k 번 슬롯이므로, 동시 재실 인원이 n 명이면 점유 슬롯은 항상 0..n-1 안에 있다. 8명이면 0~7, 10명이면 0~9 로 Goals·시나리오가 성립한다.
- **방 상세 스냅샷이 소켓 이벤트보다 오래돼 이미 `LEFT`한 참가자를 다시 `assign`해 슬롯이 누수된다는 의심** — 구독 후 `fetchRoomDetail`(`useRoomSubscription.ts:245-262`)이므로 스냅샷 시각 이후 퇴장한 사람의 `LEFT`가 HTTP 응답보다 먼저 도착하는 창(응답 지연 수십~수백 ms)에서만 가능하고, 결과는 슬롯 하나 누수(현재 참가자 간 유일성·불변성은 유지)로 만석 20명 방에서만 폴백에 닿는다. 기존 코드의 `setPlayers(detail.players)`도 같은 창에서 같은 사람을 목록에 되살리는 기존 동작이다. `[높음]`으로 세울 재현·영향을 만들지 못해 폐기.
- **Decision 4 의 "기본 `@theme`는 사용된 변수만 내보낸다" 주장이 설치 버전(4.0.9)과 다르다는 의심** — 반증. `dist/lib.js`에 `markUsedVariable`이 있고, 스캔 후보가 `--`로 시작하면 사용 변수로 표시하는 경로가 존재한다(캐시 라운드 3). 템플릿 리터럴 `var(--color-chat-${slot})`는 소스에 완성된 변수명이 등장하지 않으므로 표시되지 않는다는 설계 서술과 정합. 어느 쪽이든 리터럴 클래스 배열 방식은 동작한다.
- **`RoomChatMessage`/`ChatMessage` 타입 서술이 코드와 어긋난다는 의심** — 반증. `ChatMessage.ts:8-13,21-22`에서 `ChatMessage`(type 'message')와 `SystemMessage`의 유니온이 `RoomChatMessage`로 default export 된다. 설계 Context 의 `RoomChatMessage[]` 와 tasks 2.3 의 "`ChatMessage`에 `colorSlot` 추가"(system 메시지는 색 없음)가 모두 코드 구조와 맞는다.
- **`LEFT` 직후 같은 발신자의 `CHAT` 로 떠난 사람이 슬롯을 점유한다는 의심(라운드 2 기각 재검)** — `RoomStompController.kt:61-77` 은 멤버십 검사 없이 세션 속성만으로 발행하나, 클라이언트는 `LEFT` 수신 즉시 연결을 끊고(`useRoomSubscription.ts:155-165`) 서버 세션도 인터셉터가 관리하므로 창이 밀리초 단위. 라운드 2 와 같은 이유로 폐기.

판정: 진입 가능 — 라운드 2 지적(스펙 36° SHALL vs 실측 35.74°)이 스펙·설계·proposal 전부 35°/17° 계약으로 통일돼 해소됐고, 표가 라운드 2 와 동일해 실측이 계약을 만족하며, 슬롯 배정(Decision 1~3)·Tailwind 스캔(Decision 4)의 코드 주장은 캐시와 직접 확인으로 전부 코드와 일치한다.
