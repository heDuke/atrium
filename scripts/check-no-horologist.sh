#!/usr/bin/env bash
# Fails if any banned dependency appears in the tree:
#  - Horologist (Wear Compose M3 only)
#  - phone Material 3 (androidx.compose.material3; Wear uses androidx.wear.compose.material3)
#  - material-kolor / Material Color Utilities (color core is Atrium's own clean-room HCT)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PATTERN='androidx\.wear\.horologist|com\.google\.android\.horologist|horologist-|androidx\.compose\.material3|com\.materialkolor|material-kolor|materialkolor|material-color-utilities|com\.google\.android\.material\.color\.utilities'
HITS="$(rg -n --glob '!**/build/**' --glob '!**/.gradle/**' --glob '!**/.git/**' \
  -e "$PATTERN" \
  --glob '*.kt' --glob '*.kts' --glob '*.toml' --glob '*.gradle' --glob '*.xml' \
  . < /dev/null || true)"

if [[ -n "$HITS" ]]; then
  echo "Dependency ban violated:"
  echo "$HITS"
  exit 1
fi

echo "OK: no Horologist / phone M3 / material-kolor / MCU artifacts found."
