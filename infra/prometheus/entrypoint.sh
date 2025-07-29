#!/bin/sh

sed -i "s|\${DATA_NODE_IP}|${DATA_NODE_IP}|g" /etc/prometheus/prometheus.yml

echo "--- Generated prometheus.yml ---"
cat /etc/prometheus/prometheus.yml
echo "------------------------------"

exec "$@"
