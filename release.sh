#!/usr/bin/env bash
# Write the update manifest for the APK that build.sh produced.
# Usage: release.sh <apkUrl>   -> build/update.json (upload it and the APK to the URL in strings.xml)
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
APK=$HERE/build/launcher-signed.apk
BT=$HOME/Android/Sdk/build-tools/35.0.1
APK_URL=${1:?apkUrl, e.g. https://example.com/honda-launcher/launcher.apk}
read -r CODE NAME < <($BT/aapt2 dump badging "$APK" | sed -n "s/^package: name='[^']*' versionCode='\([0-9]*\)' versionName='\([^']*\)'.*/\1 \2/p")
SHA=$(sha256sum "$APK" | cut -d' ' -f1)
printf '{"versionCode": %s, "versionName": "%s", "apkUrl": "%s", "sha256": "%s"}\n' "$CODE" "$NAME" "$APK_URL" "$SHA" > "$HERE/build/update.json"
cp "$APK" "$HERE/build/launcher.apk"
echo "wrote $HERE/build/update.json (version $CODE $NAME)"
cat "$HERE/build/update.json"
