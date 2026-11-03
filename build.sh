#!/bin/bash
# Command-line build without Gradle (needs JDK 17 + Android SDK platform 34 + build-tools 34).
# Usage: ANDROID_HOME=~/Android/Sdk ./build.sh
set -euo pipefail
cd "$(dirname "$0")"
SDK=${ANDROID_HOME:-$HOME/Android/Sdk}
BT=$SDK/build-tools/34.0.0
JAR=$SDK/platforms/android-34/android.jar
M=app/src/main
OUT=build-cli
rm -rf $OUT && mkdir -p $OUT/gen $OUT/classes $OUT/dex
$BT/aapt2 compile --dir $M/res -o $OUT/res.zip
# the Gradle build takes the package from app/build.gradle (namespace); the CLI build injects it
sed 's#<manifest xmlns:android="http://schemas.android.com/apk/res/android">#<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.darshan.filemanager">#' \
  $M/AndroidManifest.xml > $OUT/AndroidManifest.xml
$BT/aapt2 link -I $JAR --manifest $OUT/AndroidManifest.xml -o $OUT/base.apk --java $OUT/gen \
  --min-sdk-version 24 --target-sdk-version 34 --version-code 1 --version-name 1.0 $OUT/res.zip
javac -Xlint:-options -source 8 -target 8 -bootclasspath $JAR -encoding UTF-8 -d $OUT/classes \
  $(find $M/java $OUT/gen -name '*.java')
$BT/d8 --release --min-api 24 --lib $JAR --output $OUT/dex $(find $OUT/classes -name '*.class')
cp $OUT/base.apk $OUT/unsigned.apk
(cd $OUT/dex && zip -q -X ../unsigned.apk classes.dex)
$BT/zipalign -f -p 4 $OUT/unsigned.apk $OUT/aligned.apk
KS=${KEYSTORE:-darshan-release.jks}
KS_PASS=${KS_PASS:?set KS_PASS to your keystore password}
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -alias darshan -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$KS_PASS" -keypass "$KS_PASS" -dname "CN=Darshan Corporation, O=Darshan Corporation, C=IN"
fi
$BT/apksigner sign --ks "$KS" --ks-key-alias darshan --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --out DarshanFileManager.apk $OUT/aligned.apk
$BT/apksigner verify --verbose DarshanFileManager.apk
echo "Built DarshanFileManager.apk"
