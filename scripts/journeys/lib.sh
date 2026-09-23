#!/usr/bin/env bash
# Shared helpers for the device journey runner. Sourced by run.sh.
# Screenshots are the validation artifact; the only automated PASS/FAIL
# signals are app crashes (logcat) and the unread-badge delta (journey 5).

set -u

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
APP_ID="${APP_ID:-com.capyreader.app.debug}"

ART_DIR="${ART_DIR:?ART_DIR must be set}"
REPORT="${REPORT:?REPORT must be set}"

PASS=0
FAIL=0
JOURNEY=""
LAST_DUMP=""

journey() {
  JOURNEY="$1"
  echo "### $JOURNEY" >> "$REPORT"
  echo "$JOURNEY: start" >&2
}

step() { echo "- $*" >> "$REPORT"; }

shot() {
  local name="$1"
  local file="$ART_DIR/${JOURNEY// /_}-$name.png"
  "$ADB" exec-out screencap -p > "$file" 2>/dev/null
  step "📸 $(basename "$file")"
}

# Cheap foreground check: window focus instead of the full activity dump.
foreground_ok() {
  "$ADB" shell dumpsys window 2>/dev/null | grep -q "mCurrentFocus.*$APP_ID"
}

# Every input re-verifies the foreground app and hard-aborts otherwise:
# blind taps must never reach the lock screen (or the clock).
abort_if_background() {
  if ! foreground_ok; then
    "$ADB" shell svc power stayon false 2>/dev/null
    echo "❌ ABORT: CapyReader lost the foreground (device locked?) — no further input." >&2
    step "❌ ABORT: app not in foreground — remaining steps cancelled"
    echo "" >> "$REPORT"
    echo "**ABORTED** — app left the foreground mid-journey." >> "$REPORT"
    exit 1
  fi
}

tap() { abort_if_background; "$ADB" shell input tap "$1" "$2"; }
swipe() { abort_if_background; "$ADB" shell input swipe "$@"; }
back() { abort_if_background; "$ADB" shell input keyevent 4; }

# UI dump used ONLY for the drawer badge read (journey 5). Not reliable
# enough on Compose windows for general assertions.
refresh_dump() {
  "$ADB" shell rm -f /sdcard/journey.xml >/dev/null 2>&1
  for _ in 1 2; do
    "$ADB" shell uiautomator dump /sdcard/journey.xml >/dev/null 2>&1
    LAST_DUMP=$("$ADB" shell cat /sdcard/journey.xml 2>/dev/null)
    [ -n "$LAST_DUMP" ] && return 0
    sleep 0.6
  done
  return 1
}

# Fail the journey (and the run) if the app crashed since the last check.
crash_check() {
  local crashes
  crashes=$("$ADB" logcat -d -s AndroidRuntime:E 2>/dev/null | grep -c 'FATAL EXCEPTION' || true)
  if [ "$crashes" -gt 0 ]; then
    FAIL=$((FAIL + 1)); step "❌ FAIL: $crashes FATAL exception(s) in logcat"
    "$ADB" logcat -d -s AndroidRuntime:E | tail -30 >> "$REPORT"
    "$ADB" logcat -c
  else
    PASS=$((PASS + 1)); step "✅ PASS: no crash"
  fi
}

summary() {
  echo "" >> "$REPORT"
  echo "**$JOURNEY: $PASS passed, $FAIL failed (cumulative)**" >> "$REPORT"
  echo "" >> "$REPORT"
}
