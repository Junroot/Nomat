FROM prom/prometheus:latest

# envsubst를 사용하기 위해 gettext 패키지를 설치합니다.
USER root
RUN apk add --no-cache gettext
USER nobody

# 템플릿 파일과 엔트리포인트 스크립트를 복사합니다.
COPY prometheus.yml.template /etc/prometheus/
COPY entrypoint.sh /

# 스크립트에 실행 권한을 부여합니다.
RUN chmod +x /entrypoint.sh

# 컨테이너 시작 시 entrypoint.sh를 실행하도록 설정합니다.
ENTRYPOINT ["/entrypoint.sh"]

# Prometheus 실행 명령을 기본 CMD로 설정합니다.
CMD ["--config.file=/etc/prometheus/prometheus.yml", "--storage.tsdb.path=/prometheus"]
