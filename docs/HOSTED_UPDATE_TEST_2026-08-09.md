# Hosted Android Update Test — 2026-08-09

## Outcome

The hosted update pipeline works through APK download and application-level security verification, but the physical Meta Portal firmware does not render a usable confirmation UI in its legacy Android package installer. The OTA install did not complete. Keep the hosted update manifest disabled until a different installer integration is implemented and verified on the physical Portal.

## Tested builds and device

- Device serial: `818PGA02P120ML06`
- Installed baseline before and after the test: UniquePeople `3.2`, Android `versionCode 5`
- Candidate: UniquePeople `3.4`, Android `versionCode 7`
- Package: `com.portal.slideshow`
- Candidate APK SHA-256: `789c052b4d2fb2e4c86dd9cfa13846eb29f48fb2210ba808c72eb1ec8220a1ed`
- Release certificate SHA-256: `c1e455be8cc920afb0b830e3c33fe2c289f71e86631696b4b0783eaa0c906507`
- Immutable hosted APK: `https://evzbmbfhebyftwmu.public.blob.vercel-storage.com/uniquepeople/releases/uniquepeople-3.4-vc7-789c052b4d2f.apk`
- Android source commit used for the final release build: merge commit `04a6d69`

The release was built with personal video and default photos excluded. Local `aapt2`, `apksigner`, SHA-256, and V1 compatibility checks passed before upload. A fresh download from Blob produced the same SHA-256 as the local artifact.

## What passed

1. Preview and Production returned the enabled 3.4/code 7 manifest with the expected package, APK URL, APK SHA-256, and certificate SHA-256.
2. The installed 3.2 app discovered the release and displayed `UniquePeople 3.4 is available`.
3. The APK downloaded successfully on the physical Portal.
4. The app displayed `Ready to install` and confirmed that package, version, checksum, and signing-certificate verification passed.
5. The install-source app-op was already allowed: `REQUEST_INSTALL_PACKAGES: allow`.
6. The app launched the firmware installer with a content URI from `com.portal.slideshow.update-file` and granted URI read permission.
7. The installer successfully staged the content into its private `no_backup` directory.

The new Settings > Updates UI was also tested on the Android emulator. It displayed 3.4/code 7 correctly, completed a manual hosted update check, reported the installed build as current, and produced no Android runtime crash.

## Failure boundary

After staging the APK, Portal OS opened:

`com.android.packageinstaller/.PackageInstallerActivity`

The activity remained foregrounded but displayed a blank white content area. `uiautomator` returned no root node, no install controls became available, and no package-install session completed. The device remained on 3.2/code 5 with its original `firstInstallTime` and `lastUpdateTime` unchanged.

The relevant activity sequence in system logs was:

1. `InstallStart` received the app's `content://com.portal.slideshow.update-file/update.apk` URI.
2. `InstallStaging` copied the APK successfully.
3. `DeleteStagedFileOnResult` handed the staged `file:///data/user_de/0/com.android.packageinstaller/no_backup/...apk` to the installer.
4. `PackageInstallerActivity` resumed and remained stuck with blank UI.

No checksum, package-name, version, signing-certificate, URI permission, or APK parsing rejection was observed before the blank installer screen. The failure is therefore isolated to the Portal firmware's legacy confirmation activity path.

## Current safety state

On 2026-08-09, `ANDROID_UPDATE_ENABLED=false` was applied to both Vercel Preview and Production and both environments were redeployed. Both endpoints were then verified to return:

```json
{"schemaVersion":1,"enabled":false,"packageName":"com.portal.slideshow"}
```

Leave this kill switch disabled. The uploaded immutable APK may remain in Blob, but clients cannot discover it through the disabled manifest.

## Recommended next step

Do not retry the same `ACTION_VIEW` APK handoff. Implement a new release that writes the already-verified APK into an Android `PackageInstaller.Session`, commits the session with an explicit result receiver, and handles `STATUS_PENDING_USER_ACTION`, success, and failure statuses. Test this alternative path first on the emulator and then on the physical Portal while the production kill switch remains disabled.

Because the physical Portal is still on 3.2 and does not contain Settings > Updates, installing the first build with the new installer implementation may require the existing rollback-safe ADB release-candidate script. Preserve the current 3.2 APK before any manual in-place install. Do not uninstall the app or clear its data.

Only re-enable OTA after a physical-device test proves all of the following:

- the system presents a visible confirmation UI;
- the install session returns a success result;
- `dumpsys package` reports the new version code;
- the slideshow and saved settings survive the in-place update;
- Settings > Updates reports the installed build as current.
