#!/bin/sh

sed -i "s|\${EXTERNAL_NODE_IP}|${EXTERNAL_NODE_IP}|g" /etc/prometheus/prometheus.yml

echo "--- Generated prometheus.yml ---"
cat /etc/prometheus/prometheus.yml
echo "------------------------------"

exec "$@"
