# Portal Photo Album

Fullscreen Android photo-frame app for Meta Portal-style devices.

## Default photos

If no shared album, Drive folder, or video URL is configured, the app starts with bundled default photos. Tap the screen to reveal **Settings**, then paste a shared Google Photos or Google Drive link to replace the defaults on that device.

## Shared Google Photos or Drive mode

1. In Google Photos or Google Drive, open the album/folder you want to show.
2. Use **Share** and create/copy the shared link.
3. Install and launch this app.
4. Tap the screen, open **Settings**, paste the shared link, and choose **Open shared Google Photos or Drive link directly**.
5. Save and play.

The app opens the shared link directly in a fullscreen WebView. No separate website, Google API key, or Google account is embedded in the APK.

You can also choose **Load through Photo Host viewer** to send the shared album URL to a configurable website as `albumUrl=<encoded shared album URL>`. That website is optional and secondary.

## Video fallback

The original video modes are still available:

- Built-in bundled video.
- Stream a direct `.mp4` URL.
- Download a direct `.mp4` URL once, then play offline.

## Build

```bash
./build.sh
```

The debug APK is written to `app-debug.apk`.

## Release-signed builds

V3 starts the release-signed distribution line for family installs and future hosted APK updates. Create and preserve the release keystore using [docs/RELEASE_SIGNING.md](docs/RELEASE_SIGNING.md), then build:

```bash
INCLUDE_VIDEO=0 UNIQUEPEOPLE_RELEASE=1 ./build.sh
```

`INCLUDE_VIDEO=0` is the distribution-safe build: it excludes both the personal
video and the bundled default photo set.

The release APK is written to `app-release.apk`.

Existing debug-signed V1/V2 installs cannot update directly to release-signed V3. Treat that as a planned one-time migration and preserve a rollback APK before attempting it.

V3.2 (`versionCode 5`) is the manually installed hosted-updater baseline. It checks `https://uniquepeople-web.vercel.app/api/android-update` without blocking normal slideshow startup. A newer APK is offered to Android's installer only after its HTTPS URL, package name, increasing version code, file SHA-256, and release signing certificate all match the hosted manifest and installed app.

V3.3 (`versionCode 6`) is the hosted-update proof release. Its Settings footer includes `Hosted update verified`, providing a visible confirmation after a successful V3.2-to-V3.3 update.

V3.4 (`versionCode 7`) adds Settings > Updates with installed build details, manual and pull-down checks, and installer resumption after unknown-source permission is granted. It remains on the stable V3 release-signing line and preserves existing Portal settings during an in-place hosted update.

For a clean install handoff to another computer, use `scripts/install-hosted-apk.sh`. It accepts either a public HTTPS APK URL or a local APK, requires the expected SHA-256, saves the installed APK for rollback, and performs an in-place ADB install. It does not build or sign APKs and does not require the release keystore or signing credentials.

The physical Portal's legacy package installer displayed a blank confirmation activity during the 2026-08-09 V3.4 test, so Preview and Production OTA manifests are disabled. Read [the hosted update test findings](docs/HOSTED_UPDATE_TEST_2026-08-09.md) before re-enabling them or changing the installer path.

## Production v1 stability

UniquePeople v1 is installed on a customer Portal. Before changing Android behavior, web config behavior, or rollout process, read [docs/V1_STABILITY.md](docs/V1_STABILITY.md).

Run the local compatibility guardrail before shipping APK changes:

```bash
scripts/check-v1-compatibility.sh
```
