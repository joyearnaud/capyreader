#!/usr/bin/env bash
# Shared helpers for the device journey runner. Sourced by run.sh, not executed.
# Requires: ANDROID_SERIAL-free single-device setup, adb in PATH or $ADB.

set -u

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
APP_ID="${APP_ID:-com.capyreader.app.debug}"

# --- artifact paths (set by run.sh) ---
ART_DIR="${ART_DIR:?ART_DIR must be set}"
REPORT="${REPORT:?REPORT must be set}"

PASS=0
FAIL=0
JOURNEY=""

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
  step "📸 screenshot: $(basename "$file")"
}

# Full-window UI dump on stdout.
LAST_DUMP=""

# Take ONE fresh UI dump; asserts and tap_text read the cached copy until
# the next refresh_dump. uiautomator silently fails on some Compose
# windows, so retry once and only keep non-empty fresh output.
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

has_text() {
  [[ "$LAST_DUMP" == *"text=\"[^\"]*"* ]] || return 1
  echo "$LAST_DUMP" | grep -q "text=\"[^\"]*${1}[^\"]*\""
}

# Assert a text anchor is present in the current window.
assert_text() {
  local needle="$1" label="${2:-$1}"
  refresh_dump
  if has_text "$needle"; then
    PASS=$((PASS + 1)); step "✅ PASS: $label"
  else
    FAIL=$((FAIL + 1)); step "❌ FAIL: $label (text '$needle' not found in dump)"
    shot "FAIL-$label"
  fi
}

foreground_ok() {
  "$ADB" shell dumpsys window 2>/dev/null | \
    grep -q "mCurrentFocus.*$APP_ID"
}

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

# Best-effort text tap: parse the dump for the node bounds, tap its center.
# Falls back to the given coordinates when the text is not found.
tap_text() {
  local needle="$1" fx="$2" fy="$3"
  local bounds
  refresh_dump
  bounds=$(echo "$LAST_DUMP" | tr '<' '\n<' | grep "text=\"[^\"]*$needle" | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | head -1 | grep -o '[0-9]*' | tr '\n' ' ' | sed 's/ $//')
  if [ -n "$bounds" ]; then
    local x1 y1 x2 y2
    read -r x1 y1 x2 y2 <<< "$bounds"
    tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
    step "tap_text '$needle' (matched bounds)"
  else
    tap "$fx" "$fy"
    step "tap_text '$needle' not found in dump — used fallback ($fx,$fy)"
  fi
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
  echo "**$JOURNEY: $PASS passed, $FAIL failed**" >> "$REPORT"
  echo "" >> "$REPORT"
}
