# UniquePeople v1 Stability Notes

UniquePeople v1 is live on one customer Portal. We should keep innovating, but it is our job to avoid breaking the installed v1. Treat the current behavior as a production compatibility contract. Future changes should preserve these guarantees unless we intentionally create a v2 migration plan.

## Live v1 Snapshot

- Android package: `com.portal.slideshow`
- App name: `UniquePeople`
- GitHub repo: `https://github.com/sshodhan/UniquePeople`
- Web/config site: `https://uniquepeople-web.vercel.app`
- Remote config endpoint: `https://uniquepeople-web.vercel.app/api/device-config`
- Customer Portal tested device serial: `818PGA02P1206T05`
- Customer Portal app device ID: `UP-DDA36346F304`
- Known-good Android commit: `392ea76 Reduce clock overlay coverage`
- APK path after build: `app-debug.apk`
- Build command for shareable APKs: `INCLUDE_VIDEO=0 ./build.sh` (excludes the personal video and default photos)
- Release-signed APK path after build: `app-release.apk`
- Release-signed build command: `INCLUDE_VIDEO=0 UNIQUEPEOPLE_RELEASE=1 ./build.sh`

## V2 Rollout Note

V2 is an intentional in-place upgrade for additional family Portal devices, not a separate app. It keeps the same Android package name, signing flow, preference keys, web config URLs, and local data preservation behavior so an installed V1 can be upgraded with `adb install -r` without clearing data.

- V2 Android versionCode: `2`
- V2 Android versionName: `2.0`
- V2 visible Settings title: `UniquePeople V2 Settings`
- V2 install rule: save the currently installed APK first, then use `adb install -r app-debug.apk`
- V2 rollback rule: if rollback is needed, reinstall the preserved APK for that exact Portal. If Android blocks downgrade from V2 to V1, stop and explain options before clearing data or uninstalling.
- V2 tile rail: optional local display settings for Clock, Daily greeting, Weather, Stocks, and Birthday reminders. Clock defaults on. Other tiles default off until data sources are configured. These settings must not change V1 web API fields or make album display depend on tile data.

## V3 Release-Signed Distribution Note

V3 starts Option B: the release-signed distribution line for family APK sharing and future in-app update prompts. It keeps the same package name and preference keys, but it intentionally changes the signing strategy for new distribution builds.

- V3 Android versionCode: `3`
- V3 Android versionName: `3.0`
- V3 signing rule: release APKs must be built with `UNIQUEPEOPLE_RELEASE=1` and the stable UniquePeople release keystore documented in [RELEASE_SIGNING.md](RELEASE_SIGNING.md).
- V3 debug rule: `./build.sh` may still create `app-debug.apk` for local testing, but family/customer distribution should use release-signed `app-release.apk`.
- Important migration rule: debug-signed V1/V2 installs cannot update directly to release-signed V3. If Android blocks install because signatures differ, stop and decide whether to do a planned uninstall/reinstall. Do not clear data or uninstall without explicit approval.
- V3 rollback rule: preserve the exact pre-migration APK before attempting release-signed V3. If rollback is needed after a release-signed install, reinstall a release-signed rollback from the same release keystore, or stop before any destructive action.

## V3.1 Release Note

V3.1 is an in-place update on the V3 release-signed distribution line. It preserves the package name, preference keys, device identity, remote configuration contract, and fallback behavior.

- V3.1 Android versionCode: `4`
- V3.1 Android versionName: `3.1`
- V3.1 signing rule: build and distribute with the same stable V3 release certificate; do not rotate the keystore.
- V3.1 install rule: preserve the currently installed APK first, verify certificate continuity, then install with `adb install -r app-release.apk` so app data remains intact.
- V3.1 rollback rule: reinstall the preserved release-signed APK. If Android blocks the version downgrade, stop and explain the non-destructive options before uninstalling or clearing data.

## V3.2 Hosted Updater Baseline

V3.2 is the manually installed baseline that can discover, download, verify, and hand future release-signed APKs to Android's package installer. Update checks remain optional and must never prevent the slideshow from starting or continuing offline.

- V3.2 Android versionCode: `5`
- V3.2 Android versionName: `3.2`
- V3.2 signing rule: build and distribute with the same stable V3 release certificate. A downloaded APK must match both the certificate declared by the hosted manifest and the certificate of the installed app.
- V3.2 install rule: preserve the currently installed release APK, verify certificate continuity, then manually install V3.2 with `adb install -r app-release.apk`. V3.2 cannot host-update an older build that does not yet contain the updater.
- V3.2 verification rule: require HTTPS metadata and APK URLs, an increasing version code, the established package name, matching file SHA-256, and matching release certificate SHA-256 before opening Android's installer.
- V3.2 offline rule: metadata or download failure must not interrupt photos, tiles, settings, or the assistant. The user may dismiss optional update prompts.
- V3.2 rollback rule: reinstall the preserved V3.1 release-signed APK. If Android blocks the version downgrade, stop and explain non-destructive options before uninstalling or clearing data.

## V3.3 Hosted Update Proof Release

V3.3 is the first release intended to prove the complete hosted update path from the manually installed V3.2 baseline. Its visible Settings footer reads `Hosted update verified` so successful installation can be confirmed without relying only on package metadata.

- V3.3 Android versionCode: `6`
- V3.3 Android versionName: `3.3`
- V3.3 signing rule: build and distribute with the same stable V3 release certificate used by V3.2.
- V3.3 hosted install rule: publish the immutable release APK and its verified metadata with `required=false`, then allow V3.2 to download, verify, and hand it to Android's package installer.
- V3.3 verification rule: confirm the hosted file SHA-256 and certificate SHA-256 match the locally verified release artifact before enabling the manifest.
- V3.3 rollback rule: preserve the installed V3.2 APK before the hosted update. If rollback is required and Android blocks the version downgrade, stop before uninstalling or clearing data.

## Must Not Break

1. Preserve package name `com.portal.slideshow`.
   Changing this creates a separate app install and will not preserve customer settings.

2. Preserve local settings data on install.
   Use `adb install -r app-debug.apk`. Do not uninstall or clear app data on a customer Portal unless explicitly approved.

3. Preserve remote config fallback behavior.
   The app must work if the Vercel site or network is unavailable. It should keep local settings and fall back to the default album URL when no remote config is available.

4. Preserve Google Photos direct mode.
   Google Photos must receive touch events. Do not add overlays or touch listeners that consume WebView taps, because that breaks album selection and slideshow controls.

5. Preserve remote album override behavior.
   A configured album URL from Vercel must be allowed even if it equals an older default URL. Do not reintroduce migration logic that rewrites valid remote album links.

6. Preserve optional web/config behavior.
   The app should first work independently from the website. The website is a configuration layer, not a hard dependency for display.

7. Preserve readable but compact clock behavior.
   The v1 clock should be a compact translucent panel, not a full-height black rail. It must avoid covering too much of the photo.

## Current Defaults

- V3.1 default album rule: the fallback album changes only for installs without a saved or remotely configured album. Existing `album_url` preferences and valid remote `albumUrl` values must continue to win.

- Default album URL:
  `https://photos.app.goo.gl/3BjJ4L2MdXJiZQ6P6`
- Default settings base URL:
  `https://uniquepeople-web.vercel.app/settings`
- Default assistant URL:
  `https://uniquepeople-web.vercel.app/assistant`
- Default photo host URL:
  `https://uniquepeople-web.vercel.app/photo-host`
- Default clock color:
  `#39FF14`
- Default clock font size:
  `42sp`
- Clock display format:

```text
time
day
month day
year
```

Example:

```text
3:55 PM
Wed
Aug 5
2026
```

## Rollback Checkpoints

Keep these local rollback folders intact:

- Original pre-test rollback:
  `rollback-apks/2026-08-05-portal-818PGA02P1206T05-com.portal.slideshow/base.apk`
- Known-good v1 checkpoint:
  `rollback-apks/2026-08-05-known-good-clock-rail-852e285/app-debug.apk`

The rollback folder is intentionally local and untracked. Do not delete it and do not commit APK backups to Git.

## Customer Install Rules

Before installing a new APK on a customer Portal:

1. Verify the device is visible:

```bash
adb devices
```

2. Record the installed version:

```bash
adb -s <serial> shell dumpsys package com.portal.slideshow | rg 'versionCode|versionName|firstInstallTime|lastUpdateTime'
```

3. Save a rollback copy before install if one does not already exist for that device/build:

```bash
adb -s <serial> shell pm path com.portal.slideshow
adb -s <serial> pull <device-apk-path> rollback-apks/<date-device-package>/base.apk
```

4. Install without clearing data:

```bash
adb -s <serial> install -r app-debug.apk
```

5. Launch:

```bash
adb -s <serial> shell am start -n com.portal.slideshow/.MainActivity
```

## Required Smoke Test

Run this checklist before considering any build safe for customer use:

- App launches without crash.
- Existing customer album still loads.
- Vercel per-device album change is picked up after app restart.
- If Vercel is unavailable, app still displays a usable album/defaults.
- Google Photos taps still work: slideshow/play controls, photo selection, and album UI must receive touches.
- Settings and Assistant buttons are hidden during normal display and return on tap.
- Clock is readable, compact, and does not create a large black band over the photo.
- Clock settings still save color and font size.
- QR scan button opens the camera or gives a clear failure path.
- No OpenAI API key or private token is present in APK source, client HTML, or browser source.

## Automated Guardrails

The repo includes a v1 compatibility script:

```bash
scripts/check-v1-compatibility.sh
```

The script checks the installed v1 contract, including package name, app name, stored preference keys, production config URLs, default album URL, Google Photos mode values, compact clock overlay shape, and the presence of these stability notes.

GitHub Actions runs the same script on pushes to `main` and on pull requests:

```text
.github/workflows/v1-compatibility.yml
```

This check is a tripwire, not a replacement for real testing. If it fails, either fix the regression or update this document with an intentional migration plan before changing the check.

## Vercel Config Test

To change the customer Portal album through the web config endpoint:

```bash
curl -sS -X POST https://uniquepeople-web.vercel.app/api/device-config \
  -H 'content-type: application/json' \
  --data '{
    "deviceId": "UP-DDA36346F304",
    "displayName": "Physical Portal",
    "albumUrl": "https://photos.app.goo.gl/3oSoQUkNGCRtqMmE6",
    "mode": "google_photos",
    "photoHostUrl": "https://uniquepeople-web.vercel.app/photo-host",
    "assistantUrl": "https://uniquepeople-web.vercel.app/assistant",
    "slideDurationSeconds": 8,
    "skipVideos": true,
    "controlsAutoHideSeconds": 5
  }'
```

Verify:

```bash
curl -sS 'https://uniquepeople-web.vercel.app/api/device-config?deviceId=UP-DDA36346F304'
```

Then restart the app and visually confirm the album changed.

## Server-Side Changes That Are Safe for v1

These changes can usually be made directly on the Vercel/web side without shipping a new APK, as long as the existing API response shape remains compatible:

- Update a device's `albumUrl` to another shared Google Photos or Google Drive URL.
- Update `displayName` for human-readable admin use.
- Update `photoHostUrl` as long as Google Photos direct mode still works without it.
- Update `assistantUrl` as long as the URL remains valid HTTPS and the Android Assistant button can open it.
- Update `slideDurationSeconds`, `skipVideos`, and `controlsAutoHideSeconds` for future-compatible behavior. The v1 APK should ignore fields it does not use.
- Add new optional JSON fields to `/api/device-config`. v1 should ignore unknown fields.
- Improve `/settings`, `/assistant`, or `/photo-host` HTML/CSS/JS if existing routes keep loading.
- Change OpenAI models, voices, prompts, or assistant server behavior behind the existing web endpoints, provided no client-side API key is exposed.
- Add new per-device records for additional family Portals.
- Add admin-only web UI around existing config data.

Server-side changes that need extra caution or APK regression testing:

- Renaming or removing `/api/device-config`.
- Removing `config.albumUrl`, `config.mode`, `config.photoHostUrl`, or `config.assistantUrl` from the response.
- Returning invalid JSON or changing the top-level `{ deviceId, storage, config }` shape.
- Requiring authentication or cookies for the Android app's config fetch.
- Redirecting config requests to a non-HTTPS URL.
- Making the website required for normal photo display.
- Removing existing routes `/settings`, `/assistant`, `/photo-host`, or `/config.json`.
- Changing album URLs to private links that Google Photos cannot open without manual login.
- Exposing `OPENAI_API_KEY`, Vercel Blob tokens, or other server secrets in client JS/HTML.

## Safe Change Policy

For v1 maintenance:

- Innovation is welcome, but every new feature must keep the installed v1 path working.
- New experiments should be additive, configurable, or easy to disable.
- Prefer small, targeted patches.
- Keep Android and web/config changes in separate commits when possible.
- Build and test in emulator before customer Portal install.
- Do not force-push after a build has been shared externally.
- Do not change package name, signing approach, Vercel API shape, or default URLs without writing a migration note here.
- Update this document whenever a new customer APK is installed or a new rollback checkpoint is created.
