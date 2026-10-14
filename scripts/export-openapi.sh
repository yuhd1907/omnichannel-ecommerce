#!/usr/bin/env bash
# Xuat OpenAPI spec (sinh tu code bang springdoc) cua 4 service ra docs/api/*.openapi.json.
# Chay sau khi sua API, stack local dang chay (docker compose up -d --wait): cac service mo cong
# 8081-8084 o local. Prod khong mo cong service va khong co Swagger UI.
#   bash scripts/export-openapi.sh
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p docs/api
for pair in identity:8081 product:8082 order:8083 payment:8084; do
  name=${pair%%:*}; port=${pair##*:}
  curl -sf "http://localhost:${port}/v3/api-docs" \
    | node -e "let s='';process.stdin.on('data',d=>s+=d).on('end',()=>process.stdout.write(JSON.stringify(JSON.parse(s),null,2)+'\n'))" \
    > "docs/api/${name}-service.openapi.json"
  echo "docs/api/${name}-service.openapi.json"
done
