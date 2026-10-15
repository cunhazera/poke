#!/usr/bin/env bash
# Runs every Postman collection in api-tests/collections against a running server.
# Usage: api-tests/run.sh [baseUrl]   (default: http://localhost:8080)
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
BASE_URL="${1:-http://localhost:8080}"
NEWMAN="npx --yes newman@6.2.3"

if ! curl -s -o /dev/null --max-time 5 "$BASE_URL/api/v1/pokemon?size=1"; then
  echo "Server not reachable at $BASE_URL. Start it first, e.g.:" >&2
  echo "  ./gradlew bootRun" >&2
  exit 1
fi

status=0
for collection in "$DIR"/collections/*.postman_collection.json; do
  echo "▶ $(basename "$collection")"
  $NEWMAN run "$collection" \
    --environment "$DIR/environments/local.postman_environment.json" \
    --env-var "baseUrl=$BASE_URL" \
    --reporters cli \
    --color on || status=1
done
exit $status
