#!/bin/sh

sed "s|\${DATA_NODE_IP}|${DATA_NODE_IP}|g" /etc/prometheus/prometheus.yml > /tmp/prometheus.yml

echo "--- Generated prometheus.yml ---"
cat /tmp/prometheus.yml
echo "------------------------------"

exec prometheus --config.file=/tmp/prometheus.yml --storage.tsdb.path=/prometheus
