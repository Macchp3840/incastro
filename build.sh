#!/usr/bin/env bash
# Compila Incastro senza Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
# Uso: ./build.sh            (APK in build/Incastro.apk)
#      ./build.sh test       (solo i test del motore sulla JVM)
#      ./build.sh debug      (APK debuggable in build/Incastro-debug.apk, per run-as)
# Firma di release: SIGNING_KEYSTORE=... SIGNING_KEY_ALIAS=... SIGNING_PASSWORD=... ./build.sh
set -euo pipefail
cd "$(dirname "$0")"

SDK=${ANDROID_HOME:-$HOME/Android/Sdk}
BT=$SDK/build-tools/36.0.0
JAR=$SDK/platforms/android-36/android.jar

rm -rf build/test && mkdir -p build/test
javac --release 17 -d build/test src/it/incastro/Engine.java test/it/incastro/EngineTest.java
java -cp build/test it.incastro.EngineTest
[ "${1:-}" = test ] && exit 0
DEBUG_FLAG=""
OUT=build/Incastro.apk
if [ "${1:-}" = debug ]; then
    DEBUG_FLAG="--debug-mode"
    OUT=build/Incastro-debug.apk
fi

rm -rf build/apk && mkdir -p build/apk/classes build/apk/dex
"$BT/aapt2" compile --dir res -o build/apk/res.zip
"$BT/aapt2" link build/apk/res.zip -I "$JAR" --manifest AndroidManifest.xml $DEBUG_FLAG \
    --min-sdk-version 26 --target-sdk-version 36 -o build/apk/unsigned.apk
javac --release 17 -nowarn -classpath "$JAR" -d build/apk/classes $(find src -name '*.java')
"$BT/d8" --release --min-api 26 --lib "$JAR" --output build/apk/dex $(find build/apk/classes -name '*.class')
(cd build/apk/dex && "$BT/aapt" add -k ../unsigned.apk classes.dex >/dev/null)
"$BT/zipalign" -f -p 4 build/apk/unsigned.apk build/apk/aligned.apk

if [ -n "${SIGNING_KEYSTORE:-}" ]; then
    # Firma di release: SIGNING_KEYSTORE, SIGNING_KEY_ALIAS e SIGNING_PASSWORD dall'ambiente.
    "$BT/apksigner" sign --ks "$SIGNING_KEYSTORE" --ks-key-alias "$SIGNING_KEY_ALIAS" \
        --ks-pass env:SIGNING_PASSWORD --key-pass env:SIGNING_PASSWORD --out "$OUT" build/apk/aligned.apk
else
    if [ ! -f debug.keystore ]; then
        keytool -genkeypair -keystore debug.keystore -storepass android -keypass android -alias incastro \
            -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Incastro" >/dev/null
    fi
    "$BT/apksigner" sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android \
        --out "$OUT" build/apk/aligned.apk
fi
echo "APK: $(pwd)/$OUT"
