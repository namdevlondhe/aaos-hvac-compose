#!/usr/bin/env bash
# Installs the app as a privileged system app on a writable AAOS emulator,
# so it is granted android.car.permission.CONTROL_CAR_CLIMATE and drives the real VHAL.
#
# Start the emulator first with a writable system image, for example:
#   emulator -avd <your_aaos_avd> -writable-system
set -euo pipefail

APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
APP_DIR="/system/priv-app/AaosHvacCompose"
PERMS="privileged/privapp-permissions-dev.namdev.hvac.xml"

[ -f "$APK" ] || { echo "APK not found: $APK (run ./gradlew :app:assembleDebug)"; exit 1; }

adb root
adb wait-for-device
adb remount
adb shell mkdir -p "$APP_DIR"
adb push "$APK" "$APP_DIR/AaosHvacCompose.apk"
adb push "$PERMS" /system/etc/permissions/
adb reboot
adb wait-for-device

echo "Installed. After boot, open 'Climate' from the launcher. The SIMULATED badge should be gone."
