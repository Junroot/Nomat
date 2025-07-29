#!/bin/sh

envsubst < /etc/prometheus/prometheus.yml.template > /etc/prometheus/prometheus.yml

echo "--- Generated prometheus.yml ---"
cat /etc/prometheus/prometheus.yml
echo "------------------------------"

exec "$@"
