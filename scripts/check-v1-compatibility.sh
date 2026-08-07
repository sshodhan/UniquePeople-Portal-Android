#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="$ROOT/app/src/main/AndroidManifest.xml"
MAIN="$ROOT/app/src/main/java/com/portal/slideshow/MainActivity.java"
STRINGS="$ROOT/app/src/main/res/values/strings.xml"
STABILITY_DOC="$ROOT/docs/V1_STABILITY.md"

fail() {
  echo "V1 compatibility check failed: $*" >&2
  exit 1
}

require_file() {
  local file="$1"
  [[ -f "$file" ]] || fail "missing required file: ${file#$ROOT/}"
}

require_text() {
  local file="$1"
  local text="$2"
  local why="$3"
  grep -Fq "$text" "$file" || fail "$why (${file#$ROOT/})"
}

require_absent() {
  local file="$1"
  local text="$2"
  local why="$3"
  if grep -Fq "$text" "$file"; then
    fail "$why (${file#$ROOT/})"
  fi
}

require_file "$MANIFEST"
require_file "$MAIN"
require_file "$STRINGS"
require_file "$STABILITY_DOC"

require_text "$MANIFEST" 'package="com.portal.slideshow"' \
  "package name changed; installed v1 settings would not be preserved"
require_text "$MANIFEST" 'android:versionCode="1"' \
  "v1 versionCode changed; document migration and rollback expectations first"
require_text "$MANIFEST" 'android:versionName="1.0"' \
  "v1 versionName changed; document migration and rollback expectations first"

require_text "$STRINGS" '<string name="app_name">UniquePeople</string>' \
  "app name changed from UniquePeople"

require_text "$MAIN" 'static final String PREFS = "slideshow_prefs";' \
  "SharedPreferences name changed; installed v1 settings would not be preserved"
require_text "$MAIN" 'static final String KEY_ALBUM_URL = "album_url";' \
  "album preference key changed; installed v1 album would not be preserved"
require_text "$MAIN" 'static final String KEY_DEVICE_ID = "device_id";' \
  "device ID preference key changed; per-device config pairing would break"
require_text "$MAIN" 'static final String DEFAULT_SETTINGS_BASE_URL = "https://uniquepeople-web.vercel.app/settings";' \
  "settings URL changed; update v1 stability notes and test before shipping"
require_text "$MAIN" 'static final String DEFAULT_REMOTE_CONFIG_URL = "https://uniquepeople-web.vercel.app/api/device-config";' \
  "remote config endpoint changed; v1 web config compatibility is at risk"
require_text "$MAIN" 'static final String DEFAULT_ALBUM_URL = "https://photos.app.goo.gl/qsgZFqbeTfpmWUvdA";' \
  "default album URL changed; document migration and test fallback behavior"
require_text "$MAIN" 'static final String DEFAULT_CLOCK_COLOR = "#39FF14";' \
  "default classic green clock color changed"
require_text "$MAIN" 'static final int MODE_GOOGLE_PHOTOS = 3;' \
  "Google Photos mode value changed; stored v1 mode may break"
require_text "$MAIN" 'static final int MODE_PHOTO_HOST = 4;' \
  "Photo Host mode value changed; stored v1 mode may break"

require_text "$MAIN" 'return p.getString(KEY_ALBUM_URL, DEFAULT_ALBUM_URL);' \
  "album URL getter no longer preserves configured remote/local album value"
require_absent "$MAIN" 'PREVIOUS_DEFAULT_ALBUM_URL' \
  "old default rewrite guard reintroduced; it blocked valid remote album URLs"

require_text "$MAIN" 'Google Photos must receive all album touches' \
  "touch pass-through warning comment removed; this is a v1 regression risk"
require_text "$MAIN" 'overlay.setClickable(false);' \
  "overlay may consume Google Photos touches"
require_text "$MAIN" 'FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT' \
  "clock overlay may no longer be compact"
require_text "$MAIN" 'clockChrome.setBackgroundColor(Color.argb(132, 0, 0, 0));' \
  "clock overlay opacity/coverage changed from v1 compact panel"

require_text "$STABILITY_DOC" 'UniquePeople v1 is live on one customer Portal' \
  "v1 stability document no longer describes production v1"
require_text "$STABILITY_DOC" 'Server-Side Changes That Are Safe for v1' \
  "v1 stability document is missing server-side compatibility guidance"
require_text "$STABILITY_DOC" '818PGA02P1206T05' \
  "v1 protected customer Portal serial is missing from stability notes"
require_text "$STABILITY_DOC" 'UP-DDA36346F304' \
  "v1 protected customer Portal device ID is missing from stability notes"

echo "V1 compatibility guardrails passed."
