# expose-deployed-image-version

운영 중인 백엔드 인스턴스의 빌드 git commit·branch·시각을 Spring Boot Actuator `/info` 엔드포인트로 노출하여, EC2 SSH 없이 curl 한 번으로 배포된 이미지 버전을 확인할 수 있게 한다. third-party Gradle 플러그인은 사용하지 않는다.
