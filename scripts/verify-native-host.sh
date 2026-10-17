#!/usr/bin/env bash
# Builds core/color-native's pure C++ core for the host and checks it against the Kotlin golden file.
# Needs a C++17 compiler (g++ or clang++). Run from anywhere.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
out="${TMPDIR:-/tmp}/atrium-color-host-test"
cxx="${CXX:-$(command -v g++ || command -v clang++)}"
"$cxx" -std=c++17 -O2 -Wall -Wextra -Werror -ffp-contract=off \
  "$root/core/color-native/src/main/cpp/color_core.cpp" \
  "$root/core/color-native/src/test/cpp/host_test.cpp" -o "$out"
"$out" "$root/core/color/golden/color.golden"
