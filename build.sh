#!/usr/bin/env bash
# Self-contained APK build for the Portal Photo Slideshow app.
# No Gradle, no network. Uses only the local Android SDK + a JDK.
set -e
cd "$(dirname "$0")"
PKG="com.portal.slideshow"
echo "==> Portal Slideshow build"

# ---- 1. Locate Android SDK -------------------------------------------------
SDK=""
for cand in "$ANDROID_HOME" "$ANDROID_SDK_ROOT" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
  if [ -n "$cand" ] && [ -d "$cand/platforms" ]; then SDK="$cand"; break; fi
done
if [ -z "$SDK" ]; then
  echo "ERROR: Android SDK not found. Set ANDROID_HOME, or open Android Studio > SDK Manager once."
  exit 1
fi
echo "SDK:        $SDK"

# ---- 2. Locate a JDK (prefer Android Studio's bundled JBR) -----------------
JDK_HOME=""
for cand in \
  "$JAVA_HOME" \
  "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  "$(/usr/libexec/java_home 2>/dev/null)"; do
  if [ -n "$cand" ] && [ -x "$cand/bin/javac" ]; then JDK_HOME="$cand"; break; fi
done
if [ -z "$JDK_HOME" ]; then
  echo "ERROR: No JDK with javac found. Install Temurin 17 or point JAVA_HOME at Android Studio's JBR."
  exit 1
fi
export JAVA_HOME="$JDK_HOME"
export PATH="$JDK_HOME/bin:$PATH"
echo "JDK:        $JDK_HOME"

# ---- 3. Pick newest build-tools and platform android.jar -------------------
BT_DIR="$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)"
if [ -z "$BT_DIR" ]; then echo "ERROR: no build-tools installed. In SDK Manager, install 'Android SDK Build-Tools'."; exit 1; fi
AAPT2="$BT_DIR/aapt2"; D8="$BT_DIR/d8"; ZIPALIGN="$BT_DIR/zipalign"; APKSIGNER="$BT_DIR/apksigner"
ANDROID_JAR="$(ls "$SDK"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -1)"
if [ -z "$ANDROID_JAR" ]; then echo "ERROR: no platform android.jar found. In SDK Manager install any 'Android SDK Platform'."; exit 1; fi
echo "build-tools:$BT_DIR"
echo "platform:   $ANDROID_JAR"
for t in "$AAPT2" "$D8" "$ZIPALIGN" "$APKSIGNER"; do
  [ -x "$t" ] || { echo "ERROR: missing tool: $t"; exit 1; }
done

# ---- 4. Clean workspace ----------------------------------------------------
OUT="build"
rm -rf "$OUT"; mkdir -p "$OUT/classes" "$OUT/dex"

# ---- 5. Compile + link resources (produces base APK with manifest+icon) ----
# Bundle the local video unless INCLUDE_VIDEO=0 (use 0 to make a shareable copy
# with NO personal video baked in -- users then add their own URL in Settings).
ASSET_ARGS=""
if [ "${INCLUDE_VIDEO:-1}" != "0" ] && [ -f app/src/main/assets/slideshow.mp4 ]; then
  ASSET_ARGS="-A app/src/main/assets"
  echo "video:      bundled  (run 'INCLUDE_VIDEO=0 bash build.sh' for a shareable copy without it)"
else
  echo "video:      NOT bundled (shareable build -- users set their own URL in Settings)"
fi
echo "==> aapt2 compile"
"$AAPT2" compile --dir app/src/main/res -o "$OUT/res.zip"
echo "==> aapt2 link"
"$AAPT2" link \
  -o "$OUT/base.apk" \
  -I "$ANDROID_JAR" \
  --manifest app/src/main/AndroidManifest.xml \
  --min-sdk-version 28 --target-sdk-version 29 \
  --auto-add-overlay \
  $ASSET_ARGS \
  "$OUT/res.zip"

# ---- 6. Compile Java -------------------------------------------------------
echo "==> javac"
SRcS="$(find app/src/main/java -name '*.java')"
"$JDK_HOME/bin/javac" -source 8 -target 8 -nowarn \
  -classpath "$ANDROID_JAR" \
  -d "$OUT/classes" \
  $SRcS

# ---- 7. Dex ----------------------------------------------------------------
echo "==> d8"
CLASSES="$(find "$OUT/classes" -name '*.class')"
"$D8" --release --min-api 28 --lib "$ANDROID_JAR" --output "$OUT/dex" $CLASSES

# ---- 8. Add classes.dex to the APK -----------------------------------------
echo "==> package dex"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
( cd "$OUT/dex" && zip -q "$OLDPWD/$OUT/unsigned.apk" classes.dex )

# ---- 9. Align --------------------------------------------------------------
echo "==> zipalign"
"$ZIPALIGN" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

# ---- 10. Debug keystore + sign --------------------------------------------
KS="$HOME/.android/debug.keystore"
if [ ! -f "$KS" ]; then
  echo "==> creating debug keystore"
  mkdir -p "$HOME/.android"
  "$JDK_HOME/bin/keytool" -genkeypair -v -keystore "$KS" \
    -alias androiddebugkey -storepass android -keypass android \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US"
fi
echo "==> apksigner"
"$APKSIGNER" sign \
  --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias androiddebugkey \
  --out app-debug.apk "$OUT/aligned.apk"

echo ""
echo "================================================================"
echo " BUILD OK ->  $(pwd)/app-debug.apk"
echo "================================================================"
echo "Install + launch on the Portal:"
echo "  hzdb app install -r app-debug.apk"
echo "  hzdb app launch $PKG"
