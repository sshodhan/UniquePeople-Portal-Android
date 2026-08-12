# Slideshow and AI Voice Agent for Daily Updates

UniquePeople displays a shared Google Photos or Google Drive link on a Meta Portal and can be installed from a macOS or Linux computer using Android Debug Bridge (ADB).

## Prerequisites

1. Enable developer access on the Portal. Meta's official [Build Apps for Your Portal with AI](https://developers.meta.com/horizon/blog/build-apps-for-portal-with-ai/) guide describes the device prerequisite as **Settings > Debug > ADB Enabled**.
2. Install [Android SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools) on the computer so the `adb` command is available.
3. Connect the Portal to the computer over USB and accept the debugging authorization prompt on the Portal, if one appears.
4. Confirm that exactly one physical Portal is connected:

   ```bash
   adb devices
   ```

   The Portal should appear with the status `device`. If it says `unauthorized`, unlock the Portal and accept its USB debugging prompt.

## Important: use at your own risk

UniquePeople is an independent community project. It is not an official Meta product and is not endorsed, supported, or warranted by Meta.

Sideloading software and enabling developer access can expose a device to additional security and stability risks. Installation may fail, settings or data may be lost, and device behavior may change. The simple first-time installation does not create a rollback backup. The safer upgrade option below attempts to preserve the existing APK and performs an in-place update, but successful installation, rollback, compatibility, and continued device operation are not guaranteed.

Review the source and commands before running them, preserve any data you care about, and proceed only if you understand and accept the risks. You are solely responsible for the decision to install and use this software. The project and its contributors provide it **as is**, without warranties or guarantees, and are not responsible for device damage, data loss, account issues, service interruption, or other consequences arising from its installation or use.

## First-time installation

If UniquePeople is not already installed, copy and paste this block into a
macOS or Linux shell:

```bash
# Download the release-signed APK. Stop if the download is unsuccessful.
curl --fail --location --remove-on-error -o uniquepeople.apk \
  https://evzbmbfhebyftwmu.public.blob.vercel-storage.com/uniquepeople/releases/uniquepeople-3.5-vc8-90686635498f.apk && \

# Install and launch only after a successful download.
adb install uniquepeople.apk && \
adb shell monkey -p com.portal.slideshow \
  -c android.intent.category.LAUNCHER 1
```

This is the simplest path for a new device. It downloads only the APK and uses
Android's standard ADB installer. It does not create a rollback copy or verify
the checksum automatically.

## Existing installation or verified upgrade

If UniquePeople is already installed—or if you want checksum verification and
a rollback copy—use the reusable installer:

```bash
curl -O https://raw.githubusercontent.com/sshodhan/UniquePeople-Portal-Android/main/scripts/install-hosted-apk.sh
chmod +x install-hosted-apk.sh

./install-hosted-apk.sh \
  --url https://evzbmbfhebyftwmu.public.blob.vercel-storage.com/uniquepeople/releases/uniquepeople-3.5-vc8-90686635498f.apk \
  --sha256 90686635498f2b9c8b8c2e20191c42e9ccb2a14702f8438ebe3024810f00df26 \
  --rollback-root "$PWD/rollback-apks/handoff"
```

The installer verifies the APK checksum before contacting the Portal. If UniquePeople is already installed, it saves the existing APK and package information under `rollback-apks/handoff/`, then performs an in-place update so the app's saved settings are preserved.

When installation finishes, the script prints the installed version and launches UniquePeople automatically.

## Configure the photo display

1. Create or open an album in Google Photos or a folder in Google Drive.
2. Use **Share** to create a link that the Portal can open.
3. In UniquePeople, tap the screen and open **Settings**.
4. Paste the shared link, choose **Open shared Google Photos or Drive link directly**, and save.

Only use a link whose contents you are comfortable making accessible to anyone who receives that link.

## Troubleshooting

- `adb: command not found`: install Android SDK Platform Tools and ensure its directory is on your shell's `PATH`.
- `no devices/emulators found` or `no physical ADB device found`: reconnect the USB cable, verify that ADB is enabled on the Portal, and run `adb devices` again.
- `unauthorized`: unlock the Portal and accept the USB debugging prompt, then run the installer again.
- More than one ADB device is connected: disconnect the other devices or use `adb -s PORTAL_SERIAL install uniquepeople.apk`. For the verified installer, add `--serial PORTAL_SERIAL`. The serial is shown by `adb devices`.
- `APK SHA-256 mismatch`: do not install the file. Download it again and verify that the URL and checksum match this page.

The first-time path requires only `adb` and `curl`. The verified installer also requires `bash` and either `shasum` or `sha256sum`. Neither path requires this repository, Android Studio, source code, or signing credentials.
