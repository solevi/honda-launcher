#!/usr/bin/env bash
# Build the Honda launcher APK: aapt2 -> javac -> d8 -> apksigner (own key, not the platform key).
# Usage: build.sh            -> build/launcher-signed.apk
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
SDK=${ANDROID_SDK:-$HOME/Android/Sdk}
BT=$SDK/build-tools/35.0.1
ANDROID_JAR=$SDK/platforms/android-35/android.jar
KEY=$HOME/.android/honda-launcher.jks          # our signing key, kept outside the repo
OUT=$HERE/build
rm -rf "$OUT"; mkdir -p "$OUT/gen" "$OUT/classes"

if [ ! -f "$KEY" ]; then
  keytool -genkeypair -keystore "$KEY" -storepass android -keypass android -alias honda-launcher \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Honda Launcher, O=Personal" >/dev/null
  echo "created signing key $KEY"
fi

"$BT/aapt2" compile --dir "$HERE/res" -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/unsigned.apk" -I "$ANDROID_JAR" --manifest "$HERE/AndroidManifest.xml" \
  --java "$OUT/gen" --min-sdk-version 27 --target-sdk-version 27 "$OUT/res.zip"

JAVAC=${JAVAC:-/home/lavagyan/android-studio/jbr/bin/javac}
"$JAVAC" -nowarn -source 8 -target 8 -bootclasspath "$ANDROID_JAR" -classpath "$ANDROID_JAR" \
  -d "$OUT/classes" $(find "$HERE/src" "$OUT/gen" -name '*.java')

"$BT/d8" --release --min-api 27 --lib "$ANDROID_JAR" --output "$OUT" $(find "$OUT/classes" -name '*.class')
cp "$OUT/unsigned.apk" "$OUT/app.apk"
(cd "$OUT" && zip -X -q app.apk classes.dex)   # add dex to the package (zip must be on PATH)

"$BT/zipalign" -f -p 4 "$OUT/app.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --v1-signing-enabled false --v2-signing-enabled true --ks "$KEY" --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias honda-launcher --out "$OUT/launcher-signed.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify --verbose "$OUT/launcher-signed.apk" | head -3
echo "built $OUT/launcher-signed.apk"
