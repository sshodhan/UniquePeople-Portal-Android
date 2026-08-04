# Portal Photo Album

Fullscreen Android photo-frame app for Meta Portal-style devices.

## Shared Google Photos album mode

1. In Google Photos, open an album.
2. Use **Share** and create/copy the shared album link.
3. Install and launch this app.
4. Tap the screen, open **Settings**, paste the shared album link, and choose **Load through Photo Host viewer**.
5. Save and play.

The app sends the shared album URL to a configurable Photo Host viewer as `albumUrl=<encoded shared album URL>`, then loads that viewer fullscreen. No Google API key or Google account is embedded in the APK.

You can also choose **Show a shared Google Photos album** to load the Google Photos page directly in the app.

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
