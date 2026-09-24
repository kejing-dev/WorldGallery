#!/bin/bash
# Emulator test harness: dbg.sh <cmd> [arg] [screenshot-name] [wait-seconds]
# Sends a debug broadcast to World Gallery, optionally captures a screenshot, and prints the VM state line.
DEV=${DEV:-emulator-5554}
CMD=$1; ARG=$2; SHOT=$3; WAIT=${4:-2}
if [ -n "$CMD" ]; then
  adb -s "$DEV" shell am broadcast -a com.worldgallery.spatial.DEBUG --es cmd "$CMD" --es arg "'$ARG'" >/dev/null 2>&1
fi
sleep "$WAIT"
if [ -n "$SHOT" ]; then
  mkdir -p "$(dirname "$0")/screenshots"
  pico-cli capture screenshot --out "$(dirname "$0")/screenshots/$SHOT.png" 2>&1 | tail -1
fi
adb -s "$DEV" logcat -d -s WorldGallery.Dbg:I 2>/dev/null | tail -1 | cut -c1-700
adb -s "$DEV" logcat -d 2>/dev/null | grep -E "AndroidRuntime|FATAL|WorldGallery\.(VM|Scene|Model|Main|Stage|Viewer|Bitmaps)" | grep -vE "Monkey|Using default boot|Leaving lock|VM exiting" | tail -8 | cut -c1-300
