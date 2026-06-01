# expose-application-metrics

JVM·Spring Boot 애플리케이션 메트릭을 Actuator `prometheus` endpoint로 노출하고, Grafana Alloy가 `spring-app`을 replica별로 scrape하여 Grafana Cloud Mimir로 push한다. actuator를 전용 관리 포트(dev 프로파일 `8081`)로 분리해 공개 ingress 표면에서 격리하고, Alloy metric allow-list로 시계열 카디널리티를 하드캡한다. 직전 `replace-elk-with-grafana-cloud`가 Non-Goal로 남긴 앱 레벨 메트릭 사각지대를 닫는 후속 변경.
