# configure-grafana-alerting

Grafana Cloud에 구축된 대시보드(spring-app · node-exporter · 로그) 위에 **능동적 알림**을 구성한다. 증상 기반 critical/warning 알림 규칙, Discord 2채널 라우팅, NoData·롤링배포 오탐 방지 정책을 정의하고, 대시보드·알림 규칙을 `infra/grafana/`에 export 스냅샷으로 버전 관리한다.
