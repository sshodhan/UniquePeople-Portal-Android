#!/usr/bin/env bash
# Run the isolated AUR-53 Key Attestation probe on a physical Portal.
# This performs an in-place, release-signed install and never clears app data.
set -euo pipefail
cd "$(dirname "$0")/.."

SERIAL=""
ALLOW_EMULATOR=0
ENV_FILE="${UNIQUEPEOPLE_RELEASE_ENV:-/Users/saralshodhan/projects/Release/UniquePeopleRelease/secure/release.env}"
OUTPUT="${ATTESTATION_PROBE_OUTPUT:-/private/tmp/uniquepeople-attestation-probe.json}"

while [ "$#" -gt 0 ]; do
  case "$1" in
    -s|--serial)
      SERIAL="${2:-}"
      shift 2
      ;;
    --allow-emulator)
      ALLOW_EMULATOR=1
      shift
      ;;
    --output)
      OUTPUT="${2:-}"
      shift 2
      ;;
    -h|--help)
      echo "Usage: scripts/run-attestation-probe.sh -s DEVICE_SERIAL [--allow-emulator] [--output FILE]"
      exit 0
      ;;
    *)
      echo "ERROR: unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

if [ -z "$SERIAL" ]; then
  echo "ERROR: pass the exact device serial with -s." >&2
  adb devices -l
  exit 2
fi
if [ "$ALLOW_EMULATOR" != "1" ] && [[ "$SERIAL" == emulator-* ]]; then
  echo "ERROR: the production decision requires a physical Portal; refusing emulator target." >&2
  exit 2
fi
if [ ! -f "$ENV_FILE" ]; then
  echo "ERROR: release environment not found: $ENV_FILE" >&2
  exit 2
fi
if ! adb devices | awk -v serial="$SERIAL" '$1 == serial && $2 == "device" { found=1 } END { exit !found }'; then
  echo "ERROR: device is not connected and authorized: $SERIAL" >&2
  exit 2
fi

echo "==> Loading release signing environment"
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a
export UNIQUEPEOPLE_RELEASE=1
export INCLUDE_VIDEO=0

echo "==> Building release-signed attestation probe"
./build.sh

echo "==> Installing in place (app data is preserved)"
adb -s "$SERIAL" install -r app-release.apk

echo "==> Running isolated probe"
adb -s "$SERIAL" shell rm -f \
  /sdcard/Android/data/com.portal.slideshow/files/attestation-probe.json
adb -s "$SERIAL" shell am start -W -n com.portal.slideshow/.AttestationProbeActivity
for _ in 1 2 3 4 5 6 7 8 9 10; do
  if adb -s "$SERIAL" shell test -f /sdcard/Android/data/com.portal.slideshow/files/attestation-probe.json; then
    break
  fi
  sleep 1
done

echo "==> Pulling public certificate evidence"
adb -s "$SERIAL" pull \
  /sdcard/Android/data/com.portal.slideshow/files/attestation-probe.json "$OUTPUT"
echo "Evidence: $OUTPUT"
