#!/usr/bin/env bash
# Hosted-release consistency guard: verifies that the public installation
# guide (docs/INSTALL.md) agrees with what the hosted APK URL actually
# serves. The guide pins a fixed --url and --sha256; releases that forget to
# refresh them (docs/RELEASE_CHECKLIST.md step 9) leave users installing a
# stale or missing APK. This check downloads the pinned URL and compares its
# SHA-256 against the pinned checksum.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GUIDE="$ROOT/docs/INSTALL.md"

fail() {
  echo "Hosted-release consistency check failed: $*" >&2
  exit 1
}

[[ -f "$GUIDE" ]] || fail "missing $GUIDE"

url="$(grep -Eo -- '--url https://[^[:space:]]+' "$GUIDE" | head -1 | cut -d' ' -f2 || true)"
sha="$(grep -Eo -- '--sha256 [0-9a-f]{64}' "$GUIDE" | head -1 | cut -d' ' -f2 || true)"

[[ -n "$url" ]] || fail "could not find a '--url https://...' value in docs/INSTALL.md"
[[ -n "$sha" ]] || fail "could not find a '--sha256 <64 hex>' value in docs/INSTALL.md"

echo "guide URL:    $url"
echo "guide SHA256: $sha"

case "$url" in
  https://*.public.blob.vercel-storage.com/*) ;;
  *) fail "guide URL is not a Vercel Blob public URL: $url" ;;
esac

tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT
apk="$tmp/hosted.apk"

curl --fail --silent --show-error --location --max-time 120 --output "$apk" "$url" \
  || fail "downloading the pinned URL failed — the hosted APK is missing or unreachable"

actual="$(sha256sum "$apk" | cut -d' ' -f1)"
echo "hosted SHA256: $actual"

[[ "$actual" == "$sha" ]] \
  || fail "checksum mismatch: guide pins $sha but the hosted APK is $actual — update docs/INSTALL.md (see docs/RELEASE_CHECKLIST.md step 9)"

# The guide names the APK with the first 12 chars of its SHA-256; drift here
# means the filename lies about the content even if the pinned pair matches.
prefix="${sha:0:12}"
case "$url" in
  *"$prefix"*) ;;
  *) fail "hosted filename does not contain the checksum prefix $prefix — re-upload following the naming convention in docs/RELEASE_CHECKLIST.md step 9" ;;
esac

echo "Hosted-release consistency check passed: the install guide matches the hosted APK."
