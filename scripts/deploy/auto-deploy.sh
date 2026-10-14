#!/usr/bin/env bash
# Pull-based CD: cron chay moi 2 phut tren VPS.
# Nha cung cap VPS chan ket noi tu GitHub Actions vao (SSH :22) va tu VPS ra Actions (Azure),
# nen VPS tu keo code + image moi tu github.com / ghcr.io (chieu nay thong).
set -euo pipefail
cd /home/huy/omnichannel-ecommerce

exec 9>/tmp/auto-deploy.lock
flock -n 9 || exit 0   # lan truoc chua xong thi bo qua

APPS="api-gateway identity-service product-service order-service payment-service notification-service frontend"
DC="docker compose -f docker-compose.yml -f docker-compose.prod.yml"
digest() {
  for s in $APPS; do
    docker image inspect -f '{{.Id}}' "ghcr.io/yuhd1907/omnichannel-$s:latest" 2>/dev/null || echo none
  done | md5sum
}

OLD_CODE=$(git rev-parse HEAD)
git fetch -q origin main
git reset -q --hard origin/main
NEW_CODE=$(git rev-parse HEAD)

OLD_IMG=$(digest)
if ! PULL_OUT=$($DC pull -q $APPS 2>&1); then
  echo "=== $(date '+%F %T') PULL FAILED"
  echo "$PULL_OUT"
  exit 1
fi
NEW_IMG=$(digest)

if [ "$OLD_CODE" != "$NEW_CODE" ] || [ "$OLD_IMG" != "$NEW_IMG" ]; then
  echo "=== $(date '+%F %T') deploy: code ${OLD_CODE:0:7} -> ${NEW_CODE:0:7}"
  $DC up -d --remove-orphans
  docker image prune -f > /dev/null
  $DC ps --format "table {{.Name}}\t{{.Status}}"
fi