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

## Production v1 stability

UniquePeople v1 is installed on a customer Portal. Before changing Android behavior, web config behavior, or rollout process, read [docs/V1_STABILITY.md](docs/V1_STABILITY.md).

Run the local compatibility guardrail before shipping APK changes:

```bash
scripts/check-v1-compatibility.sh
```
