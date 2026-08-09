#!/usr/bin/env bash
# Portable handoff installer for an already-built, release-signed APK.
# This script never loads a keystore or builds/signs an APK.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PACKAGE_NAME="com.portal.slideshow"
ROLLBACK_ROOT="$ROOT/rollback-apks/handoff"
SERIAL=""
APK_PATH=""
APK_URL=""
EXPECTED_SHA256=""
TEMP_DIR=""

usage() {
  cat <<'EOF'
Usage:
  scripts/install-hosted-apk.sh --url HTTPS_URL --sha256 SHA256 [-s DEVICE_SERIAL]
  scripts/install-hosted-apk.sh --apk APK_PATH --sha256 SHA256 [-s DEVICE_SERIAL]

Options:
  --url HTTPS_URL       Download an already-signed APK over HTTPS.
  --apk APK_PATH        Install an already-downloaded APK.
  --sha256 SHA256       Required expected SHA-256 for the APK.
  -s, --serial SERIAL   Target ADB device. Auto-detects one physical device if omitted.
  --rollback-root DIR   Rollback output directory (default: repository/rollback-apks/handoff).
  --package NAME        Expected installed package (default: com.portal.slideshow).
  -h, --help            Show this help.

The script verifies SHA-256 before contacting the device, saves the currently
installed APK and package metadata, performs an in-place `adb install -r`, and
launches the app. It does not require source code, signing credentials, or a
release keystore; only this script, adb, curl for URL installs, and the expected
SHA-256 are required.
EOF
}

cleanup() {
  if [ -n "$TEMP_DIR" ] && [ -d "$TEMP_DIR" ]; then
    rm -rf -- "$TEMP_DIR"
  fi
}
trap cleanup EXIT

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --url)
      [ "$#" -ge 2 ] || fail "--url requires a value"
      APK_URL="$2"
      shift 2
      ;;
    --apk)
      [ "$#" -ge 2 ] || fail "--apk requires a value"
      APK_PATH="$2"
      shift 2
      ;;
    --sha256)
      [ "$#" -ge 2 ] || fail "--sha256 requires a value"
      EXPECTED_SHA256="$(printf '%s' "$2" | tr '[:upper:]' '[:lower:]')"
      shift 2
      ;;
    -s|--serial)
      [ "$#" -ge 2 ] || fail "$1 requires a value"
      SERIAL="$2"
      shift 2
      ;;
    --rollback-root)
      [ "$#" -ge 2 ] || fail "--rollback-root requires a value"
      ROLLBACK_ROOT="$2"
      shift 2
      ;;
    --package)
      [ "$#" -ge 2 ] || fail "--package requires a value"
      PACKAGE_NAME="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      fail "unknown argument: $1"
      ;;
  esac
done

command -v adb >/dev/null 2>&1 || fail "adb is required"
[ -n "$EXPECTED_SHA256" ] || fail "--sha256 is required"
printf '%s' "$EXPECTED_SHA256" | grep -Eq '^[0-9a-f]{64}$' || fail "--sha256 must be 64 hexadecimal characters"

if [ -n "$APK_URL" ] && [ -n "$APK_PATH" ]; then
  fail "pass either --url or --apk, not both"
elif [ -n "$APK_URL" ]; then
  case "$APK_URL" in
    https://*) ;;
    *) fail "--url must use HTTPS" ;;
  esac
  command -v curl >/dev/null 2>&1 || fail "curl is required for --url"
  TEMP_DIR="$(mktemp -d)"
  APK_PATH="$TEMP_DIR/update.apk"
  echo "==> Downloading APK over HTTPS"
  curl --fail --location --proto '=https' --tlsv1.2 --output "$APK_PATH" "$APK_URL"
elif [ -z "$APK_PATH" ]; then
  fail "pass --url or --apk"
fi

[ -f "$APK_PATH" ] || fail "APK not found: $APK_PATH"

if command -v shasum >/dev/null 2>&1; then
  ACTUAL_SHA256="$(shasum -a 256 "$APK_PATH" | awk '{print $1}')"
elif command -v sha256sum >/dev/null 2>&1; then
  ACTUAL_SHA256="$(sha256sum "$APK_PATH" | awk '{print $1}')"
else
  fail "shasum or sha256sum is required"
fi

[ "$ACTUAL_SHA256" = "$EXPECTED_SHA256" ] || fail "APK SHA-256 mismatch (expected $EXPECTED_SHA256, got $ACTUAL_SHA256)"
echo "==> APK SHA-256 verified: $ACTUAL_SHA256"

if [ -z "$SERIAL" ]; then
  DEVICE_LIST="$(adb devices | awk 'NR > 1 && $2 == "device" && $1 !~ /^emulator-/ { print $1 }')"
  DEVICE_COUNT="$(printf '%s\n' "$DEVICE_LIST" | awk 'NF { count++ } END { print count + 0 }')"
  [ "$DEVICE_COUNT" -gt 0 ] || fail "no physical ADB device found; connect one or pass --serial"
  [ "$DEVICE_COUNT" -eq 1 ] || fail "multiple physical ADB devices found; pass --serial"
  SERIAL="$(printf '%s\n' "$DEVICE_LIST" | awk 'NF { print; exit }')"
fi

echo "==> Target device: $SERIAL"
adb -s "$SERIAL" get-state >/dev/null

STAMP="$(date +%Y-%m-%d-%H%M%S)"
ROLLBACK_DIR="$ROLLBACK_ROOT/$SERIAL-$STAMP-before-handoff"
INSTALLED_PATHS="$(adb -s "$SERIAL" shell pm path "$PACKAGE_NAME" 2>/dev/null | sed 's/^package://' | tr -d '\r' || true)"

if [ -n "$INSTALLED_PATHS" ]; then
  mkdir -p "$ROLLBACK_DIR"
  echo "==> Saving rollback files to $ROLLBACK_DIR"
  adb -s "$SERIAL" shell dumpsys package "$PACKAGE_NAME" > "$ROLLBACK_DIR/package-before.txt"
  while IFS= read -r INSTALLED_PATH; do
    [ -n "$INSTALLED_PATH" ] || continue
    FILE_NAME="$(basename "$INSTALLED_PATH")"
    adb -s "$SERIAL" pull "$INSTALLED_PATH" "$ROLLBACK_DIR/$FILE_NAME" >/dev/null
    if command -v shasum >/dev/null 2>&1; then
      shasum -a 256 "$ROLLBACK_DIR/$FILE_NAME" > "$ROLLBACK_DIR/$FILE_NAME.sha256"
    else
      sha256sum "$ROLLBACK_DIR/$FILE_NAME" > "$ROLLBACK_DIR/$FILE_NAME.sha256"
    fi
  done <<EOF
$INSTALLED_PATHS
EOF
else
  echo "==> $PACKAGE_NAME is not installed; no rollback APK is available"
fi

echo "==> Installing in place with adb install -r"
adb -s "$SERIAL" install -r "$APK_PATH"

echo "==> Installed package"
adb -s "$SERIAL" shell dumpsys package "$PACKAGE_NAME" | sed -n '/versionCode=/p;/versionName=/p;/firstInstallTime=/p;/lastUpdateTime=/p'

echo "==> Launching $PACKAGE_NAME"
adb -s "$SERIAL" shell monkey -p "$PACKAGE_NAME" -c android.intent.category.LAUNCHER 1 >/dev/null

echo "Handoff install complete."
