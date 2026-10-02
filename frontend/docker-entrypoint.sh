#!/bin/sh
set -e
export PORT="${PORT:-80}"
export BACKEND_URL="${BACKEND_URL:-http://127.0.0.1:8080}"
envsubst '${PORT} ${BACKEND_URL}' < /opt/nginx/default.conf.template > /etc/nginx/conf.d/default.conf
