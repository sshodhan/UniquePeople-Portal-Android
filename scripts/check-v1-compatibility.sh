#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="$ROOT/app/src/main/AndroidManifest.xml"
MAIN="$ROOT/app/src/main/java/com/portal/slideshow/MainActivity.java"
UPDATER="$ROOT/app/src/main/java/com/portal/slideshow/AndroidUpdateManager.java"
ENROLLMENT="$ROOT/app/src/main/java/com/portal/slideshow/DeviceEnrollment.java"
PORTAL_LOGGER="$ROOT/app/src/main/java/com/portal/slideshow/PortalLogger.java"
ASSISTANT="$ROOT/app/src/main/java/com/portal/slideshow/AssistantActivity.java"
VOICE_HARNESS="$ROOT/app/src/main/java/com/portal/slideshow/PortalVoiceHarnessActivity.java"
STRINGS="$ROOT/app/src/main/res/values/strings.xml"
STABILITY_DOC="$ROOT/docs/V1_STABILITY.md"
RELEASE_CHECKLIST="$ROOT/docs/RELEASE_CHECKLIST.md"
REVIEW_CHECKLIST="$ROOT/.github/claude-review.md"

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
require_file "$RELEASE_CHECKLIST"
require_file "$REVIEW_CHECKLIST"

require_text "$REVIEW_CHECKLIST" 'Should the finalized signed APK' \
  "pre-PR review must ask whether the finalized APK should be published"
require_text "$REVIEW_CHECKLIST" '2d Production distribution:' \
  "pre-PR review summary must record the production distribution decision"
require_text "$RELEASE_CHECKLIST" 'Opening or merging an Android PR never publishes an APK automatically.' \
  "release checklist must keep production publication explicit"
require_text "$RELEASE_CHECKLIST" 'npm run upload:android-release -- <PATH_TO_APP_RELEASE_APK>' \
  "release checklist must reference the reusable Blob upload workflow"

require_text "$MANIFEST" 'package="com.portal.slideshow"' \
  "package name changed; installed v1 settings would not be preserved"
if grep -Fq 'android:versionCode="1"' "$MANIFEST"; then
  require_text "$MANIFEST" 'android:versionName="1.0"' \
    "v1 versionName changed unexpectedly"
elif grep -Fq 'android:versionCode="2"' "$MANIFEST"; then
  require_text "$MANIFEST" 'android:versionName="2.0"' \
    "v2 versionName must be 2.0"
  require_text "$STABILITY_DOC" '## V2 Rollout Note' \
    "v2 version bump requires an explicit rollout note"
  require_text "$STABILITY_DOC" 'V2 rollback rule' \
    "v2 version bump requires rollback expectations"
elif grep -Fq 'android:versionCode="3"' "$MANIFEST"; then
  require_text "$MANIFEST" 'android:versionName="3.0"' \
    "v3 versionName must be 3.0"
  require_text "$STABILITY_DOC" '## V3 Release-Signed Distribution Note' \
    "v3 version bump requires an explicit release signing note"
  require_text "$STABILITY_DOC" 'V3 signing rule' \
    "v3 release signing requires documented signing expectations"
  require_text "$STABILITY_DOC" 'debug-signed V1/V2 installs cannot update directly to release-signed V3' \
    "v3 must document the one-time signing migration"
elif grep -Fq 'android:versionCode="4"' "$MANIFEST"; then
  require_text "$MANIFEST" 'android:versionName="3.1"' \
    "v3.1 versionName must be 3.1"
  require_text "$STABILITY_DOC" '## V3.1 Release Note' \
    "v3.1 version bump requires an explicit release note"
  require_text "$STABILITY_DOC" 'V3.1 rollback rule' \
    "v3.1 requires documented rollback expectations"
  require_text "$STABILITY_DOC" 'same stable V3 release certificate' \
    "v3.1 must preserve the V3 signing line"
elif grep -Fq 'android:versionCode="5"' "$MANIFEST"; then
  require_text "$MANIFEST" 'android:versionName="3.2"' \
    "v3.2 versionName must be 3.2"
  require_text "$STABILITY_DOC" '## V3.2 Hosted Updater Baseline' \
    "v3.2 version bump requires an explicit updater release note"
  require_text "$STABILITY_DOC" 'V3.2 signing rule' \
    "v3.2 must preserve the stable release signing line"
  require_text "$STABILITY_DOC" 'V3.2 rollback rule' \
    "v3.2 requires documented rollback expectations"
elif grep -Fq 'android:versionCode="6"' "$MANIFEST"; then
  require_text "$MANIFEST" 'android:versionName="3.3"' \
    "v3.3 versionName must be 3.3"
  require_text "$STABILITY_DOC" '## V3.3 Hosted Update Proof Release' \
    "v3.3 version bump requires an explicit hosted update proof note"
  require_text "$STABILITY_DOC" 'V3.3 signing rule' \
    "v3.3 must preserve the stable release signing line"
  require_text "$STABILITY_DOC" 'V3.3 rollback rule' \
    "v3.3 requires documented rollback expectations"
  require_text "$ROOT/app/src/main/java/com/portal/slideshow/SettingsActivity.java" \
    'Hosted update verified' \
    "v3.3 proof release must include a visible verification marker"
elif grep -Fq 'android:versionCode="7"' "$MANIFEST"; then
  require_text "$MANIFEST" 'android:versionName="3.4"' \
    "v3.4 versionName must be 3.4"
  require_text "$STABILITY_DOC" '## V3.4 Settings Update Flow' \
    "v3.4 version bump requires an explicit settings update release note"
  require_text "$STABILITY_DOC" 'V3.4 signing rule' \
    "v3.4 must preserve the stable release signing line"
  require_text "$STABILITY_DOC" 'V3.4 settings preservation rule' \
    "v3.4 must document preservation of installed settings"
  require_text "$STABILITY_DOC" 'V3.4 hosted install rule' \
    "v3.4 must document the hosted installation procedure"
  require_text "$STABILITY_DOC" 'V3.4 rollback rule' \
    "v3.4 requires documented rollback expectations"
elif grep -Fq 'android:versionCode="8"' "$MANIFEST"; then
  require_text "$MANIFEST" 'android:versionName="3.5"' \
    "v3.5 versionName must be 3.5"
  require_text "$STABILITY_DOC" '## V3.5 Attested Marin Enrollment' \
    "v3.5 version bump requires an explicit enrollment release note"
  require_text "$STABILITY_DOC" 'V3.5 signing rule' \
    "v3.5 must preserve the stable release signing line"
  require_text "$STABILITY_DOC" 'V3.5 settings preservation rule' \
    "v3.5 must document preservation of installed settings"
  require_text "$STABILITY_DOC" 'V3.5 deployment rule' \
    "v3.5 must document coordinated web-first deployment"
  require_text "$STABILITY_DOC" 'V3.5 rollback rule' \
    "v3.5 requires documented rollback expectations"
else
  fail "unsupported Android versionCode; document migration and rollback expectations first"
fi

require_text "$STRINGS" '<string name="app_name">UniquePeople</string>' \
  "app name changed from UniquePeople"

require_text "$MAIN" 'static final String PREFS = "slideshow_prefs";' \
  "SharedPreferences name changed; installed v1 settings would not be preserved"
require_text "$MAIN" 'static final String KEY_ALBUM_URL = "album_url";' \
  "album preference key changed; installed v1 album would not be preserved"
require_text "$MAIN" 'static final String KEY_DEVICE_ID = "device_id";' \
  "device ID preference key changed; per-device config pairing would break"
require_text "$MAIN" 'static final String KEY_MARIN_ENABLED = "marin_enabled";' \
  "Android must retain the canonical Marin enable flag"
require_text "$MAIN" 'static final String DEFAULT_SETTINGS_BASE_URL = "https://uniquepeople-web.vercel.app/settings";' \
  "settings URL changed; update v1 stability notes and test before shipping"
require_text "$MAIN" 'static final String DEFAULT_REMOTE_CONFIG_URL = "https://uniquepeople-web.vercel.app/api/device-config";' \
  "remote config endpoint changed; v1 web config compatibility is at risk"
require_text "$MAIN" 'static final String DEFAULT_ALBUM_URL = "https://photos.app.goo.gl/3BjJ4L2MdXJiZQ6P6";' \
  "default album URL changed; document migration and test fallback behavior"
require_text "$STABILITY_DOC" 'V3.1 default album rule' \
  "default album change requires documented preservation of saved and remote album values"
require_text "$MAIN" 'static final String DEFAULT_CLOCK_COLOR = "#39FF14";' \
  "default classic green clock color changed"
require_text "$MAIN" 'static final int MODE_GOOGLE_PHOTOS = 3;' \
  "Google Photos mode value changed; stored v1 mode may break"
require_text "$MAIN" 'static final int MODE_PHOTO_HOST = 4;' \
  "Photo Host mode value changed; stored v1 mode may break"
require_file "$UPDATER"
require_file "$ENROLLMENT"
require_file "$PORTAL_LOGGER"
require_file "$ASSISTANT"
require_file "$VOICE_HARNESS"
require_text "$MANIFEST" 'android:name=".PortalVoiceHarnessActivity"' \
  "physical voice harness entry point must remain explicit"
require_text "$MANIFEST" 'android:permission="android.permission.DUMP"' \
  "physical voice harness entry point must remain shell-protected"
require_text "$ASSISTANT" 'addJavascriptInterface(new PortalVoiceHarnessBridge(), "PortalVoiceHarness")' \
  "physical voice harness bridge must remain installed"
require_text "$VOICE_HARNESS" 'isAllowedAssistantUrl' \
  "physical voice harness must validate target and restore URLs"
require_text "$VOICE_HARNESS" 'assistant_url_base64' \
  "physical voice harness must use shell-safe URL transport"
require_text "$VOICE_HARNESS" 'isProductionRestoreUrl' \
  "physical voice harness must retain a bounded production restore path"
require_text "$ENROLLMENT" 'KeyStore.getInstance("AndroidKeyStore")' \
  "Portal enrollment identity must remain in Android Keystore"
require_text "$ENROLLMENT" '.put("deviceId", deviceId)' \
  "Portal enrollment must bind the existing Settings device ID"
require_text "$ENROLLMENT" '.put("action", "attest-complete")' \
  "Portal enrollment must use the attested completion contract"
require_text "$ENROLLMENT" '.setAttestationChallenge(Base64.decode(challenge, Base64.DEFAULT))' \
  "Portal enrollment must bind the server challenge into the identity key"
require_text "$ENROLLMENT" 'signer.update(challengeToken.getBytes(StandardCharsets.UTF_8))' \
  "Portal enrollment must prove possession of the attested private key"
require_text "$ENROLLMENT" 'if (!beginEnrollment(callback)) return;' \
  "Portal enrollment must remain single-flight across concurrent launch and retry actions"
require_text "$ENROLLMENT" 'pendingChallengeExpired(context)' \
  "Portal enrollment must discard expired pending transactions before retrying"
require_text "$ROOT/app/src/main/java/com/portal/slideshow/AssistantActivity.java" \
  'appendQueryParameter("memoryKey", memoryKey)' \
  "Marin must receive the enrolled device memory credential"
require_text "$ROOT/app/src/main/java/com/portal/slideshow/SettingsActivity.java" \
  'assistantNav = nav("Assistant")' \
  "Settings must retain the dedicated Assistant tab"
require_text "$ROOT/app/src/main/java/com/portal/slideshow/SettingsActivity.java" \
  'DeviceEnrollment.savedMemoryKey(this)' \
  "Assistant settings must show the actual enrollment state"
require_text "$ROOT/app/src/main/java/com/portal/slideshow/SettingsActivity.java" \
  'DeviceEnrollment.setMarinEnabled' \
  "Assistant settings must update the server-backed Marin flag"
require_text "$PORTAL_LOGGER" '"/api/log-client-error"' \
  "Android diagnostics must use the existing sanitized logging framework"
require_text "$ENROLLMENT" '"enrollment_failed"' \
  "enrollment failures must remain observable"
require_text "$ENROLLMENT" '"marin_toggle_failed"' \
  "Marin setting failures must remain observable"
require_text "$ENROLLMENT" '"X-HTTP-Method-Override", "PATCH"' \
  "Android Marin toggles must use the HttpURLConnection-compatible PATCH override"
require_text "$ROOT/app/src/main/java/com/portal/slideshow/AssistantActivity.java" \
  '"assistant_page_failed"' \
  "Assistant WebView failures must remain observable"
require_text "$ROOT/app/src/main/java/com/portal/slideshow/AssistantActivity.java" \
  '"assistant_memoryless_fallback"' \
  "Marin must remain available when memory enrollment fails"
require_file "$ROOT/app/src/main/java/com/portal/slideshow/UpdateFileProvider.java"
require_text "$MANIFEST" 'android.permission.REQUEST_INSTALL_PACKAGES' \
  "hosted updater baseline must declare package-install request permission"
require_text "$UPDATER" '"https://uniquepeople-web.vercel.app/api/android-update"' \
  "updater must use the production HTTPS manifest endpoint"
require_text "$UPDATER" 'manifest.versionCode <= currentVersionCode(activity)' \
  "updater must ignore equal or older releases"
require_text "$UPDATER" 'sha256(apk).equals(manifest.sha256)' \
  "updater must verify the complete APK checksum"
require_text "$UPDATER" '!PACKAGE_NAME.equals(archive.packageName)' \
  "updater must verify the downloaded package name"
require_text "$UPDATER" '!archiveCertificate.equals(manifest.certificateSha256)' \
  "updater must verify the manifest release certificate"
require_text "$UPDATER" '!archiveCertificate.equals(installedCertificate)' \
  "updater must verify certificate continuity with the installed app"
require_text "$UPDATER" 'connection.setInstanceFollowRedirects(false);' \
  "updater must not follow an unvalidated redirect"

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
