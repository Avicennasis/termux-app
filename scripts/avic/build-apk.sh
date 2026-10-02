#!/usr/bin/env bash
# Private signing happens locally, never with the upstream public test key.
set -euo pipefail
AVIC_ROOT=$(cd -- "$(dirname -- "$0")/../.." && pwd)
AVIC_SIGNING=${AVIC_SIGNING_DIR:-/home/avicennasis/.local/share/termux-avic-signing}
AVIC_SDK=${ANDROID_HOME:-/home/avicennasis/Android/Sdk}
AVIC_OUTPUT=${1:?Pass an output APK path}
cd "$AVIC_ROOT"
python3 scripts/avic/verify-bootstrap.py scripts/avic/bootstrap-aarch64.zip >/dev/null
cp scripts/avic/bootstrap-aarch64.zip app/src/main/cpp/bootstrap-aarch64.zip
JAVA_HOME=${AVIC_BUILD_JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64} ./gradlew :app:assembleRelease
AVIC_UNSIGNED=app/build/outputs/apk/release/termux-avic_apt-android-7-release_arm64-v8a-unsigned.apk
python3 scripts/avic/verify-bootstrap.py scripts/avic/bootstrap-aarch64.zip --apk "$AVIC_UNSIGNED" >/dev/null
"$AVIC_SDK/build-tools/36.0.0/zipalign" -p -f 4 "$AVIC_UNSIGNED" "$AVIC_OUTPUT.aligned"
"$AVIC_SDK/build-tools/36.0.0/apksigner" sign --ks "$AVIC_SIGNING/termux-avic.p12" \
    --ks-key-alias termux-avic --ks-pass "file:$AVIC_SIGNING/keystore-password" \
    --out "$AVIC_OUTPUT" "$AVIC_OUTPUT.aligned"
rm -- "$AVIC_OUTPUT.aligned"
"$AVIC_SDK/build-tools/36.0.0/apksigner" verify --verbose --print-certs "$AVIC_OUTPUT"
sha256sum "$AVIC_OUTPUT"
