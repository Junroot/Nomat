## MODIFIED Requirements

### Requirement: 게임 중 끊긴 기존 멤버의 재접속은 입장 차단의 영향을 받지 않는다

시스템은 방이 `PLAYING`인 동안에도, 끊겼지만 재접속 유예 시간 내에 있는 기존 멤버의 재연결을 허용해야(SHALL) 한다. 재접속은 `RoomJoinChannelInterceptor`에서 유예 예약 취소(또는 동일 방 세션 확인) 경로로 처리되어 `Room.join()`을 호출하지 않으므로, PLAYING 입장 가드의 영향을 받지 않는다. 활성 세션(`ActiveSessionManager`, Redis)과 유예 예약(Redis `rooms:pending-leaves`, `room-reconnect-grace` 스펙이 소유)이 이 판별을 담당하며, **판별은 재접속 CONNECT가 어느 백엔드 인스턴스에 도착하든 같은 결과**여야(MUST) 한다.

#### Scenario: 유예 시간 내 재접속은 게임 중에도 허용된다
- **WHEN** `PLAYING` 상태의 방에서 멤버가 연결이 끊긴 뒤 유예 시간 내에 같은 방으로 다시 CONNECT
- **THEN** 예약된 퇴장이 취소되고 멤버십이 유지되어야 한다
- **AND** `Room.join()`이 호출되지 않으므로 PLAYING 입장 거부가 적용되지 않아야 한다

#### Scenario: 끊김을 처리한 인스턴스와 다른 인스턴스로 재접속해도 허용된다
- **WHEN** `PLAYING` 상태의 방에서 멤버의 끊김이 인스턴스 A에서 처리되어 유예가 예약된 뒤, 유예 시간 내에 인스턴스 B로 같은 방에 CONNECT
- **THEN** B가 Redis의 유예 예약을 취소해 재접속을 허용해야 한다
- **AND** `Room.join()`이 호출되지 않아 "게임 중에는 입장할 수 없습니다"나 "이미 방에 입장한 플레이어입니다"로 거부되지 않아야 한다
