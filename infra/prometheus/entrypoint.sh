#!/bin/sh

mv /etc/prometheus/prometheus.yml /etc/prometheus/prometheus.yml.template

envsubst < /etc/prometheus/prometheus.yml.template > /etc/prometheus/prometheus.yml

echo "--- Generated prometheus.yml ---"
cat /etc/prometheus/prometheus.yml
echo "------------------------------"

exec "$@"
