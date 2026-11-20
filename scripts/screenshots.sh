#!/usr/bin/env bash
# Captures FoldTerm screenshots on an emulator using the built-in demo data.
# Run by .github/workflows/screenshots.yml; output goes to shots/.
set -x
APK=$(ls app/build/outputs/apk/full/debug/*.apk | head -1)
PKG=dev.foldterm.launcher.debug
ACT=$PKG/dev.foldterm.launcher.MainActivity
mkdir -p shots

adb root || true
sleep 3
adb wait-for-device
adb install -r -g "$APK"

# fixed 09:35 so "happening now" and the agenda look like a working morning
adb shell settings put global auto_time 0
adb shell settings put global auto_time_zone 0
adb shell "date 112009352026.00" || adb shell "toybox date 112009352026.00" || true
adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true

# tidy status bar (System UI demo mode)
adb shell settings put global sysui_demo_allowed 1
dm() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
dm enter
dm clock -e hhmm 0935
dm battery -e level 86 -e plugged false
dm network -e wifi show -e level 4 -e mobile show -e datatype none -e level 4
dm notifications -e visible false

shot() {
  local name=$1; shift
  adb shell am start -n "$ACT" "$@" >/dev/null
  sleep 5
  adb exec-out screencap -p > "shots/$name.png"
}

adb emu unfold || true
sleep 3
shot home-phosphor   --ez demo true --es theme phosphor --es section home
shot home-pastel     --es theme pastel --es section home
shot calendar        --es theme phosphor --es section cal
shot alerts          --es section notifs
shot media           --es section media
shot weather         --es section weather
shot screen-time     --es section usage
shot people          --es theme paper --es section people
shot notes           --es theme amber --es section notes
shot controls        --es theme phosphor --es section controls
shot drawer          --es section home --ez drawer true
shot prompt          --es section home --es prompt "maya"
shot prompt-convert  --es section home --es prompt "100 usd in aud"
shot settings        --es theme pastel --es section settings

adb emu fold || true
sleep 6
shot cover-home      --es theme phosphor --es section home
shot cover-alerts    --es section notifs
shot cover-pastel    --es theme pastel --es section home
adb emu unfold || true

ls -la shots
