#!/usr/bin/env bash
# Deploy (hoac quay lai) mot phien ban image tren VPS.
#
# Job "Deploy to VPS" trong .github/workflows/docker.yml SSH vao VPS, dua repo ve dung commit
# vua build roi chay:  bash scripts/deploy/deploy.sh sha-<7 ky tu commit>
# Chay tay de quay lai ban cu:  bash scripts/deploy/deploy.sh sha-abc1234
#   (tag con tren GHCR; xem tag dang chay: grep IMAGE_TAG .env)
#
# Cac buoc:
#  1. pull image cua tag moi (loi -> dung, he thong dang chay khong bi dong toi);
#  2. up -d --wait (cho healthcheck) + goi thu gateway va frontend;
#  3. thanh cong -> ghi IMAGE_TAG vao .env de moi lenh docker compose chay tay sau nay
#     dung dung ban dang chay; that bai -> up lai tag truoc do va thoat ma 1 (workflow bao do).
# Luu y: rollback chi doi image. Migration Flyway da chay thi khong tu dao nguoc, nen migration
# phai tuong thich nguoc (chi them cot/bang, khong xoa/doi ten trong cung mot lan deploy).
set -euo pipefail

NEW_TAG="${1:?usage: deploy.sh <image-tag, vd sha-abc1234>}"
cd "$(dirname "$0")/../.."

ENV_FILE=.env
REPO=ghcr.io/yuhd1907/omnichannel
APPS="api-gateway identity-service product-service order-service payment-service notification-service frontend"

compose() { docker compose -f docker-compose.yml -f docker-compose.prod.yml "$@"; }

current_tag() { sed -n 's/^IMAGE_TAG=//p' "$ENV_FILE" | tail -n 1; }

save_tag() {
  sed -i '/^IMAGE_TAG=/d' "$ENV_FILE"
  if [ -s "$ENV_FILE" ] && [ -n "$(tail -c 1 "$ENV_FILE")" ]; then echo >> "$ENV_FILE"; fi
  echo "IMAGE_TAG=$1" >> "$ENV_FILE"
}

healthy() {
  local gw
  gw="$(curl -fsS --max-time 10 http://127.0.0.1:8080/actuator/health)" || return 1
  [[ "$gw" == *'"status":"UP"'* ]] || return 1
  curl -fsS --max-time 10 -o /dev/null http://127.0.0.1:3001/
}

# Bien moi truong IMAGE_TAG uu tien hon dong IMAGE_TAG trong .env
run_tag() {
  IMAGE_TAG="$1" compose up -d --remove-orphans --wait --wait-timeout 300 && healthy
}

touch "$ENV_FILE"
PREV_TAG="$(current_tag)"
PREV_TAG="${PREV_TAG:-latest}"
echo "=== $(date '+%F %T') deploy: $PREV_TAG -> $NEW_TAG"

IMAGE_TAG="$NEW_TAG" compose pull $APPS

if run_tag "$NEW_TAG"; then
  save_tag "$NEW_TAG"
  # Don image cu: chi giu tag dang chay va tag truoc do (de con quay lai nhanh)
  docker images --format '{{.Repository}}:{{.Tag}}' \
    | grep "^$REPO-" | grep -v -e ":$NEW_TAG\$" -e ":$PREV_TAG\$" -e ':<none>$' \
    | xargs -r docker rmi > /dev/null 2>&1 || true
  docker image prune -f > /dev/null
  compose ps --format 'table {{.Name}}\t{{.Status}}'
  echo "=== deploy OK: $NEW_TAG"
else
  echo "=== deploy FAILED: $NEW_TAG -> quay lai $PREV_TAG" >&2
  compose ps --format 'table {{.Name}}\t{{.Status}}' >&2 || true
  if run_tag "$PREV_TAG"; then
    echo "=== rollback OK: dang chay $PREV_TAG" >&2
  else
    echo "=== ROLLBACK FAILED: kiem tra tay (docker compose ps, docker logs)" >&2
  fi
  exit 1
fi
