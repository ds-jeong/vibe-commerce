#!/bin/sh
set -e

export PORT="${PORT:-80}"
export BACKEND_URL="${BACKEND_URL:-http://127.0.0.1:8080}"
BACKEND_URL="${BACKEND_URL%/}"
export BACKEND_URL
export BACKEND_HOST="$(printf '%s' "$BACKEND_URL" | sed 's|^https://||; s|^http://||; s|/.*||')"

envsubst '${PORT} ${BACKEND_URL} ${BACKEND_HOST}' \
  < /opt/nginx/default.conf.template \
  > /etc/nginx/conf.d/default.conf

exec nginx -g 'daemon off;'
