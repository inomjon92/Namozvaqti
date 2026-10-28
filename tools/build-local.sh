#!/usr/bin/env bash
# Android Gradle plaginisiz APK yig'ish (aapt2 + javac + dx + apksigner).
# Kerak: ANDROID_JAR (API 34 android.jar yoki robolectric android-all-14), KEYSTORE, KS_PASS.
set -euo pipefail
cd "$(dirname "$0")/.."
BT=$(ls -d /usr/lib/android-sdk/build-tools/* | tail -1)
: "${ANDROID_JAR:?ANDROID_JAR kerak}"
: "${KEYSTORE:?KEYSTORE kerak}"
: "${KS_PASS:?KS_PASS kerak}"
VERSION_CODE=${VERSION_CODE:-1}
VERSION_NAME=${VERSION_NAME:-1.0}
SRC=app/src/main
OUT=build-local
rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/classes"

sed 's|<manifest |<manifest package="uz.namozvaqti.app" |' "$SRC/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"
"$BT/aapt2" compile --dir "$SRC/res" -o "$OUT/res.zip"
"$BT/aapt2" link -I "$ANDROID_JAR" --manifest "$OUT/AndroidManifest.xml" \
  --min-sdk-version 26 --target-sdk-version 34 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  --java "$OUT/gen" -o "$OUT/base.apk" "$OUT/res.zip"
javac -nowarn --release 8 -encoding UTF-8 -classpath "$ANDROID_JAR" \
  -d "$OUT/classes" $(find "$OUT/gen" "$SRC/java" -name '*.java')
"$BT/dx" --dex --min-sdk-version=26 --output="$OUT/classes.dex" "$OUT/classes"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT" && zip -q -j unsigned.apk classes.dex)
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --out "$OUT/namozvaqti.apk" "$OUT/aligned.apk"
"$BT/apksigner" verify "$OUT/namozvaqti.apk"
ls -la "$OUT/namozvaqti.apk"
