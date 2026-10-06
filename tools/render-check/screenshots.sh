#!/usr/bin/env bash
# Regenerates screenshots of the whole game without a device or emulator:
#   1. a bot plays all three stages; the real renderer's GL commands and the HUD state at 28 key moments are recorded
#   2. the GL trace is replayed in headless Chromium (WebGL2 on SwiftShader)  -> 3D frames
#   3. the real HUD (the Canvas code of the app) is drawn on transparent bitmaps by Robolectric -> overlays
#   4. compose.py lays each overlay over its 3D frame                          -> <out>/shots/<name>.png
#
# usage: tools/render-check/screenshots.sh [outDir]      (default: tools/render-check/out)
# needs: JDK 17 + Android SDK (see README), Node with Playwright + Chromium, python3 with Pillow
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
out="${1:-$root/tools/render-check/out}"
cd "$root"

./gradlew :core:journeyTrace -q
node tools/render-check/render.mjs core/build/traces/journey.bin "$out/journey" all
./gradlew :app:testDebugUnitTest --tests '*HudOverlayTest' --rerun -q
python3 tools/render-check/compose.py "$out/journey" app/build/hud core/build/traces/journey.txt "$out/shots"
echo "screenshots written to $out/shots"
