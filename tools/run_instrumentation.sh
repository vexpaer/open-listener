#!/usr/bin/env bash
# Run inside one shell: emulator-runner evaluates separate script lines independently.
set -u
instrumentation_exit=0
./gradlew --no-daemon connectedDebugAndroidTest || instrumentation_exit=$?
adb pull /sdcard/Download/openlistener-ui screenshots || true
exit "$instrumentation_exit"
