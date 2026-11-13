#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

AAPT2=/usr/local/bin/aapt2
R8=/tmp/r8.zip
ANDROID_JAR=/tmp/android.jar
OUT=target-attach-4-frida.apk
BUILD=bin/build
APK=bin/target-attach.apk

rm -rf "$BUILD" bin/classes && mkdir -p "$BUILD" bin/classes

# 1. compile java
javac -source 8 -target 8 -nowarn \
  -cp "$ANDROID_JAR" \
  -d bin/classes \
  src/com/mayugoro/targetattach/MainActivity.java

# 2. dex via r8
mapfile -t CLASSFILES < <(find bin/classes -name '*.class')
java -cp "$R8" com.android.tools.r8.D8 \
  --release --min-api 21 \
  --lib "$ANDROID_JAR" \
  --output "$BUILD" \
  "${CLASSFILES[@]}"

# 3. compile + link resources (icon)
"$AAPT2" compile --dir res -o "$BUILD/res.zip"
"$AAPT2" link \
  -o "$APK" \
  -I "$ANDROID_JAR" \
  --manifest AndroidManifest.xml \
  -R "$BUILD/res.zip" \
  --auto-add-overlay

# 4. inject classes.dex (pakai jar, zip nggak ada di sistem)
cp "$BUILD/classes.dex" .
/opt/jdk25/bin/jar uf "$APK" classes.dex
rm -f classes.dex

# 5. zipalign + sign
zipalign -f 4 "$APK" "$OUT"
KEYSTORE=/root/projek_apk/myxl-cli/apk/bypass.keystore
apksigner sign \
  --ks "$KEYSTORE" \
  --ks-pass "pass:bypass123" \
  --key-pass "pass:bypass123" \
  --ks-key-alias bypass \
  --min-sdk-version 21 \
  --out "$OUT.signed.apk" "$OUT"
mv -f "$OUT.signed.apk" "$OUT"

printf 'built: %s\n' "$(pwd)/$OUT"