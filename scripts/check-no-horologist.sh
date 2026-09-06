#!/usr/bin/env bash
# Fails if any Horologist coordinate or import appears in the tree.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PATTERN='androidx\.wear\.horologist|com\.google\.android\.horologist|horologist-'
HITS="$(rg -n --glob '!**/build/**' --glob '!**/.gradle/**' --glob '!**/.git/**' \
  -e "$PATTERN" \
  --glob '*.kt' --glob '*.kts' --glob '*.toml' --glob '*.gradle' --glob '*.xml' \
  || true)"

if [[ -n "$HITS" ]]; then
  echo "Horologist ban violated:"
  echo "$HITS"
  exit 1
fi

echo "OK: no Horologist artifacts found."
