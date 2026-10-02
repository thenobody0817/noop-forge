#!/usr/bin/env bash
# Regenerates the Spo2Display parity oracle from the real Swift source.
#
# The Swift engine is compiled STANDALONE (it imports Foundation and nothing else), driven over the
# full cross product of its four inputs, and its own answers are written out as the fixture that both
# platform test suites read. That makes the fixture the Swift build's answer rather than a second
# derivation of the same rule, which is the whole point: two independent reimplementations agreeing is
# weaker evidence than one implementation pinned by a committed expectation.
#
# Requires a Swift toolchain. It does NOT require one at test time or in CI - the fixture is committed,
# and both suites read the file, so Swift is needed only when the engine changes.
#
# Usage:  Tools/oracle/gen_spo2_display_oracle.sh
#
# Requires docker (the pinned swift:6.0 image) or a local swiftc on PATH.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENGINE_REL="Packages/StrandAnalytics/Sources/StrandAnalytics/Spo2Display.swift"
HARNESS_REL="Tools/oracle/gen_spo2_display_oracle.swift"
ENGINE="$REPO_ROOT/$ENGINE_REL"
HARNESS="$REPO_ROOT/$HARNESS_REL"

[ -f "$ENGINE" ] || { echo "missing engine: $ENGINE_REL" >&2; exit 1; }

ANDROID_TEST_RES="$REPO_ROOT/android/app/src/test/resources"
mkdir -p "$ANDROID_TEST_RES"

SOURCE_COMMIT="$(git -C "$REPO_ROOT" rev-parse --short HEAD)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

cp "$ENGINE" "$WORK/Spo2Display.swift"
cp "$HARNESS" "$WORK/main.swift"

# swiftVersionString / sourceCommit are injected so the harness stays a single compilable file.
# The toolchain string is resolved here rather than by a shell substitution inside the Swift literal,
# because the fixture must record the version of the build that actually produced it.
if command -v swift >/dev/null 2>&1; then
  TOOLCHAIN="$(swift --version 2>/dev/null | head -1)"
else
  TOOLCHAIN="6.0 (docker image swift:6.0)"
fi
# Escape for a Swift string literal: the version contains no quotes today, but a backslash would break it.
TOOLCHAIN_ESCAPED="${TOOLCHAIN//\\/\\\\}"
TOOLCHAIN_ESCAPED="${TOOLCHAIN_ESCAPED//\"/\\\"}"

cat > "$WORK/shim.swift" <<SHIM
import Foundation
let swiftVersionString = "$TOOLCHAIN_ESCAPED"
let sourceCommit = "$SOURCE_COMMIT"
SHIM

OUT="$WORK/spo2_display_oracle.json"

if command -v swiftc >/dev/null 2>&1; then
  echo "building with local swiftc" >&2
  swiftc -O "$WORK/Spo2Display.swift" "$WORK/shim.swift" "$WORK/main.swift" -o "$WORK/gen"
  "$WORK/gen" > "$OUT"
else
  echo "building with docker swift:6.0" >&2
  docker run --rm -v "$WORK:/w" -w /w swift:6.0 \
    sh -c 'swiftc -O Spo2Display.swift shim.swift main.swift -o gen && ./gen' > "$OUT"
fi

# ONE canonical copy, in the Android test tree. The Swift suite reaches it by walking up from #filePath,
# the same way LocalDayWindowsTests.swift reaches its own oracle, so the two platforms cannot drift onto
# two different files.
cp "$OUT" "$ANDROID_TEST_RES/spo2_display_oracle.json"

echo "wrote:" >&2
echo "  android/app/src/test/resources/spo2_display_oracle.json" >&2