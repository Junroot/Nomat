#!/bin/sh

sed -i "s|\${DATA_NODE_IP}|${DATA_NODE_IP}|g" /etc/prometheus/prometheus.yml > /etc/prometheus/prometheus.yml.tmp

mv /etc/prometheus/prometheus.yml.tmp /etc/prometheus/prometheus.yml

echo "--- Generated prometheus.yml ---"
cat /etc/prometheus/prometheus.yml
echo "------------------------------"

exec prometheus --config.file=/etc/prometheus/prometheus.yml --storage.tsdb.path=/prometheus
