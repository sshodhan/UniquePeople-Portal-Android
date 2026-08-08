#!/usr/bin/env bash
# Build, install, and launch a release-signed UniquePeople APK on a Portal.
# This is the fast release-candidate loop for the developer Portal.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PKG="com.portal.slideshow"
ENV_FILE="${UNIQUEPEOPLE_RELEASE_ENV:-/Users/saralshodhan/projects/Release/UniquePeopleRelease/secure/release.env}"
ROLLBACK_ROOT="${UNIQUEPEOPLE_ROLLBACK_ROOT:-/Users/saralshodhan/projects/Release/UniquePeopleRelease/rollback}"
SERIAL=""
SKIP_BUILD=0

usage() {
  cat <<'EOF'
Usage:
  scripts/install-release-candidate.sh [-s DEVICE_SERIAL] [--skip-build]

Examples:
  scripts/install-release-candidate.sh
  scripts/install-release-candidate.sh -s 818PGA02P120ML06
  scripts/install-release-candidate.sh -s 818PGA02P120ML06 --skip-build

Environment:
  UNIQUEPEOPLE_RELEASE_ENV   Defaults to /Users/saralshodhan/projects/Release/UniquePeopleRelease/secure/release.env
  UNIQUEPEOPLE_ROLLBACK_ROOT Defaults to /Users/saralshodhan/projects/Release/UniquePeopleRelease/rollback
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    -s|--serial)
      SERIAL="${2:-}"
      shift 2
      ;;
    --skip-build)
      SKIP_BUILD=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "ERROR: Unknown argument: $1"
      usage
      exit 1
      ;;
  esac
done

if [ -z "$SERIAL" ]; then
  mapfile -t DEVICES < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }' | grep -v '^emulator-' || true)
  if [ "${#DEVICES[@]}" -eq 0 ]; then
    echo "ERROR: No physical Portal device found. Connect ADB or pass -s <serial>."
    adb devices
    exit 1
  fi
  if [ "${#DEVICES[@]}" -gt 1 ]; then
    echo "ERROR: More than one physical device is connected. Pass -s <serial>."
    adb devices
    exit 1
  fi
  SERIAL="${DEVICES[0]}"
fi

echo "==> Target Portal: $SERIAL"
adb -s "$SERIAL" get-state >/dev/null

if [ "$SKIP_BUILD" -eq 0 ]; then
  if [ ! -f "$ENV_FILE" ]; then
    echo "ERROR: Release env file not found: $ENV_FILE"
    echo "Create it with the UNIQUEPEOPLE_KEYSTORE, UNIQUEPEOPLE_KEY_ALIAS, UNIQUEPEOPLE_KEYSTORE_PASSWORD, and UNIQUEPEOPLE_KEY_PASSWORD exports."
    exit 1
  fi
  echo "==> Loading release signing environment"
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a

  echo "==> Building release-signed APK"
  INCLUDE_VIDEO=0 UNIQUEPEOPLE_RELEASE=1 ./build.sh
fi

if [ ! -f app-release.apk ]; then
  echo "ERROR: app-release.apk was not found."
  exit 1
fi

echo "==> New APK metadata"
if command -v aapt2 >/dev/null 2>&1; then
  aapt2 dump badging app-release.apk | sed -n '1,3p'
else
  SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
  AAPT2="$(ls -d "$SDK"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1)"
  if [ -x "$AAPT2" ]; then "$AAPT2" dump badging app-release.apk | sed -n '1,3p'; fi
fi

echo "==> Installed package before install"
INSTALLED_PATH="$(adb -s "$SERIAL" shell pm path "$PKG" 2>/dev/null | sed 's/^package://' | tr -d '\r' | head -1 || true)"
if [ -n "$INSTALLED_PATH" ]; then
  adb -s "$SERIAL" shell dumpsys package "$PKG" | sed -n '/versionCode=/p;/versionName=/p;/firstInstallTime=/p;/lastUpdateTime=/p' || true
  STAMP="$(date +%Y-%m-%d-%H%M%S)"
  ROLLBACK_DIR="$ROLLBACK_ROOT/developer-portal-$SERIAL-$STAMP-before-release-candidate"
  mkdir -p "$ROLLBACK_DIR"
  echo "==> Saving rollback APK to $ROLLBACK_DIR/base.apk"
  adb -s "$SERIAL" pull "$INSTALLED_PATH" "$ROLLBACK_DIR/base.apk" >/dev/null
  shasum -a 256 "$ROLLBACK_DIR/base.apk" > "$ROLLBACK_DIR/base.apk.sha256"
else
  echo "Package is not currently installed; no rollback APK to pull."
fi

echo "==> Installing release candidate"
adb -s "$SERIAL" install -r app-release.apk

echo "==> Installed package after install"
adb -s "$SERIAL" shell dumpsys package "$PKG" | sed -n '/versionCode=/p;/versionName=/p;/firstInstallTime=/p;/lastUpdateTime=/p'

echo "==> Launching UniquePeople"
adb -s "$SERIAL" shell monkey -p "$PKG" 1 >/dev/null

echo ""
echo "Release-candidate install complete."
