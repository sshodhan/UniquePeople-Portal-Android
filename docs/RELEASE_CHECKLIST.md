# Signed Release and Portal Deployment Checklist

The recoverable, end-to-end release process. The everyday command to remember
is:

```bash
scripts/install-release-candidate.sh -s <PORTAL_SERIAL>
```

## 1. Sync and validate

- Fetch and rebase onto `origin/main`.
- Confirm the worktree is clean.
- Run the V1 compatibility and pre-PR checks
  (`scripts/check-v1-compatibility.sh`, `.github/claude-review.md`).

## 2. Prepare the release

- Increase `versionCode`.
- Update `versionName`.
- Confirm the package remains `com.portal.slideshow`.
- Never change the release signing certificate.

## 3. Confirm signing configuration

- Environment file (release machine only):
  `~/projects/Release/UniquePeopleRelease/secure/release.env`
- Keystore (release machine only):
  `~/projects/Release/UniquePeopleRelease/secure/uniquepeople-release.jks`
- Never commit either file or their passwords. See `docs/RELEASE_SIGNING.md`.

## 4. Connect the intended Portal

```bash
adb devices
```

Record the physical Portal serial before installing.

## 5. Build, preserve rollback, install, and launch

```bash
scripts/install-release-candidate.sh -s <PORTAL_SERIAL>
```

Keep this helper as the primary release-candidate workflow. It loads
`release.env`, builds `app-release.apk`, records installed metadata, pulls the
existing APK into the rollback folder, installs with `adb install -r`, and
launches UniquePeople.

## 6. Verify the release certificate

```bash
APKSIGNER="$ANDROID_HOME/build-tools/$(ls "$ANDROID_HOME/build-tools" | sort -V | tail -1)/apksigner"
"$APKSIGNER" verify --print-certs app-release.apk
```

Confirm the SHA-256 certificate fingerprint matches prior releases.

## 7. Verify the installed build

```bash
adb -s <PORTAL_SERIAL> shell dumpsys package com.portal.slideshow
```

Confirm the expected `versionCode` and `versionName`.

## 8. Smoke test

- Slideshow loads and Google Photos remains touchable.
- Settings open; device ID and saved configuration remain intact.
- Clock and tiles load.
- Assistant opens; microphone and camera permissions work.
- Stop, mute, and close controls work.
- Offline/default behavior still works.

## 9. Publish the hosted install build

The public installation guide (`docs/INSTALL.md`) pins a fixed APK URL and
SHA-256. Every public release must refresh them — this is the step that is
easy to forget, and `scripts/check-hosted-release.sh` (run weekly in CI and on
any PR touching the guide) will go red if the guide drifts from what the
hosted URL actually serves.

- Compute the release APK's SHA-256:
  `shasum -a 256 app-release.apk` (macOS) / `sha256sum app-release.apk` (Linux).
- Upload the APK to the Vercel Blob store under
  `uniquepeople/releases/uniquepeople-<versionName>-<first-12-of-sha256>.apk`.
  Blob URLs are immutable — each release is a new file, never an overwrite.
- Update the `--url` and `--sha256` values in the Install block of
  `docs/INSTALL.md`. The generic `scripts/install-hosted-apk.sh` itself should
  not need changes.
- Run `scripts/check-hosted-release.sh` locally to confirm the guide, the
  hosted URL, and the checksum agree.
- Test the guide's copy-and-paste install block end to end on a real Portal.
- Merge the documentation update.

## 10. Preserve the release

Archive the final signed APK and record its version, commit SHA, APK SHA-256,
certificate fingerprint, date, and Portal serial. Keep the pre-install
rollback APK unchanged.

## 11. Safety rules

- Do not uninstall the existing app.
- Do not clear app data.
- Do not install a debug-signed APK over a release installation.
- Stop if Android reports a signature mismatch or downgrade.
