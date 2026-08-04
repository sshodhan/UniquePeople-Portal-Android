# Portal Photo Album

Fullscreen Android photo-frame app for Meta Portal-style devices.

## Shared Google Photos album mode

1. In Google Photos, open an album.
2. Use **Share** and create/copy the shared album link.
3. Install and launch this app.
4. Tap the screen, open **Settings**, paste the shared album link, and choose **Show a shared Google Photos album**.
5. Save and play.

The app loads the shared album URL in a fullscreen WebView. No Google API key or Google account is embedded in the APK.

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
