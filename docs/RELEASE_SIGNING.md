# UniquePeople Release Signing

UniquePeople V3 starts the release-signed distribution line. Release-signed APKs are the path for hosted APK downloads and future in-app update prompts.

## Why This Exists

Android only installs an APK update over an existing app when all of these match:

- Same package name: `com.portal.slideshow`
- Same signing certificate
- New APK has a higher `versionCode`

Changing signing certificates creates a different trust line. A debug-signed V1/V2 install cannot update directly to release-signed V3.

## One-Time Keystore Creation

Create the keystore outside the app git repo, under the shared release area:

```bash
mkdir -p /Users/saralshodhan/projects/Release/UniquePeopleRelease/secure
chmod 700 /Users/saralshodhan/projects/Release/UniquePeopleRelease/secure

keytool -genkeypair \
  -v \
  -keystore /Users/saralshodhan/projects/Release/UniquePeopleRelease/secure/uniquepeople-release.jks \
  -alias uniquepeople \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Record the keystore password and key password in a password manager. Do not commit the `.jks` file or the passwords.

## Release Build Environment

Set these environment variables before building:

```bash
export UNIQUEPEOPLE_KEYSTORE=/Users/saralshodhan/projects/Release/UniquePeopleRelease/secure/uniquepeople-release.jks
export UNIQUEPEOPLE_KEY_ALIAS=uniquepeople
export UNIQUEPEOPLE_KEYSTORE_PASSWORD='REPLACE_WITH_KEYSTORE_PASSWORD'
export UNIQUEPEOPLE_KEY_PASSWORD='REPLACE_WITH_KEY_PASSWORD'
```

Then build:

```bash
INCLUDE_VIDEO=0 UNIQUEPEOPLE_RELEASE=1 ./build.sh
```

The release APK is written to:

```text
app-release.apk
```

Local debug builds still work:

```bash
./build.sh
```

The debug APK is still written to:

```text
app-debug.apk
```

## Verify Signing

Use the Android SDK build-tools `apksigner`:

```bash
APKSIGNER="$ANDROID_HOME/build-tools/$(ls "$ANDROID_HOME/build-tools" | sort -V | tail -1)/apksigner"
"$APKSIGNER" verify --print-certs app-release.apk
```

Save the printed certificate SHA-256 fingerprint in private release notes. Future release APKs must show the same certificate fingerprint.

## Migration Rule

For existing debug-signed V1/V2 customer Portals:

1. Preserve a rollback APK first.
2. Export or record the device ID and current web settings.
3. Install release-signed V3 as a planned migration.
4. If Android blocks install because signatures differ, stop and decide whether to uninstall/reinstall. Do not clear data or uninstall without explicit approval.

For new family Portal installs:

- Use release-signed `app-release.apk`.
- Keep using the same release keystore for every future APK.

## Update Manifest Rule

Every hosted APK must have a higher `android:versionCode` than the last release-signed APK. The Vercel update manifest should point only to release-signed APKs from this signing line.
