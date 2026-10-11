#!/usr/bin/env bash
# Shared helpers for the device journey runner. Sourced by run.sh.
# Screenshots are the validation artifact. The automated signals are:
#   - crashes: FATAL lines naming this app's package (crash_check)
#   - screen state: text anchors read from the UI dump (expect_screen, tap_text)
#   - journey 5 only: the unread-badge delta
# A failed assertion marks the journey FAILED and suppresses every later input
# in that journey, so a wrong screen is never tapped further (see README).

set -u

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
APP_ID="${APP_ID:-com.capyreader.app.debug}"

ART_DIR="${ART_DIR:?ART_DIR must be set}"
REPORT="${REPORT:?REPORT must be set}"

PASS=0
FAIL=0
JOURNEY=""
LAST_DUMP=""
# Set by fail_journey: the rest of the journey is skipped, the run continues.
SKIP=0

journey() {
  JOURNEY="$1"
  # Every journey starts unskipped: a failed assertion stops the rest of that
  # journey, never the ones after it.
  SKIP=0
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

# Start every journey from a known screen. Without this, a journey inherits
# whatever the previous one left behind — that is how journeys 5 and 6 walked
# into the reader on 2026-10-11 and still reported PASS.
skip_if_failed() { [ "$SKIP" -eq 1 ] && return 1; return 0; }

# Record the failure, keep the reason in the report, and go quiet for the rest
# of the journey. The run itself continues: each journey now resets its own
# state, so one bad screen no longer invalidates the ones after it.
fail_journey() {
  FAIL=$((FAIL + 1)); SKIP=1
  step "❌ FAIL: $1"
  if dump_ui; then
    printf '%s' "$LAST_DUMP" | tr '>' '\n' | grep -o 'text="[^"]\+"' | sort -u | head -10 \
      | sed 's/^/-   seen: /' >> "$REPORT"
  fi
}

# Drop anything already in the buffer: stale system-app crashes from days ago
# made journey 1 report a false FAIL on 2026-10-11.
logcat_reset() { "$ADB" logcat -c 2>/dev/null || true; }

tap() { skip_if_failed || return 0; abort_if_background; "$ADB" shell input tap "$1" "$2"; }
swipe() { skip_if_failed || return 0; abort_if_background; "$ADB" shell input swipe "$@"; }
back() { skip_if_failed || return 0; abort_if_background; "$ADB" shell input keyevent 4; }

# UI dump: the source of every text anchor and of the journey-5 badge. Retried
# because it comes back empty while a Compose window animates.
dump_ui() {
  local attempt
  for attempt in 1 2 3; do
    "$ADB" shell rm -f /sdcard/journey.xml >/dev/null 2>&1
    "$ADB" shell uiautomator dump /sdcard/journey.xml >/dev/null 2>&1
    LAST_DUMP=$("$ADB" shell cat /sdcard/journey.xml 2>/dev/null)
    [ -n "$LAST_DUMP" ] && return 0
    sleep 0.8
  done
  return 1
}

match_count() { printf '%s' "$LAST_DUMP" | grep -o -F "$1" | wc -l | tr -d ' '; }

# Assert the screen we expect before acting on it. `anchor` is an exact
# attribute match, e.g. 'text="Résumés"' — never a substring, so a label that
# changes in the app fails loudly instead of matching something else.
expect_screen() {
  local label="$1" anchor="$2" n
  skip_if_failed || return 0
  if ! dump_ui; then
    fail_journey "$label: UI dump failed (dump failure, not a missing anchor)"
    return 1
  fi
  n=$(match_count "$anchor")
  if [ "$n" -eq 0 ]; then
    fail_journey "$label: expected screen not found (no $anchor)"
    return 1
  fi
  step "✅ screen: $label"
  return 0
}

# Tap the node carrying an exact anchor instead of a hardcoded pixel: the
# layout moves on every upstream merge, labels move with it. Ambiguity is a
# failure, never a coin flip — and there is no pixel fallback, so an anchor
# that disappears is reported rather than masked by a stale coordinate.
tap_text() {
  local label="$1" anchor="$2" n node bounds l t r b
  skip_if_failed || return 0
  if ! dump_ui; then
    fail_journey "$label: UI dump failed (dump failure, not a missing anchor)"
    return 1
  fi
  n=$(match_count "$anchor")
  if [ "$n" -eq 0 ]; then
    fail_journey "$label: anchor missing ($anchor)"
    return 1
  fi
  if [ "$n" -gt 1 ]; then
    fail_journey "$label: anchor ambiguous ($anchor matched $n nodes)"
    return 1
  fi
  node=$(printf '%s' "$LAST_DUMP" | tr '>' '\n' | grep -F "$anchor" | head -1)
  bounds=$(printf '%s' "$node" | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | head -1)
  if [ -z "$bounds" ]; then
    fail_journey "$label: anchor without bounds ($anchor)"
    return 1
  fi
  set -- $(printf '%s' "$bounds" | tr -cs '0-9' ' ')
  l=$1; t=$2; r=$3; b=$4
  # A degenerate box or a tap in the status-bar band is a broken dump, not a tap.
  if [ "$l" -ge "$r" ] || [ "$t" -ge "$b" ] || [ "$(( (t + b) / 2 ))" -lt 120 ]; then
    fail_journey "$label: unsafe bounds ($bounds)"
    return 1
  fi
  abort_if_background
  "$ADB" shell input tap "$(( (l + r) / 2 ))" "$(( (t + b) / 2 ))"
}

# Text of the node containing a screen point: lets a journey name what it is
# about to open, then assert it comes back.
text_at() {
  local x="$1" y="$2" node bounds l t r b text
  dump_ui || return 1
  while IFS= read -r node; do
    bounds=$(printf '%s' "$node" | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | head -1)
    [ -z "$bounds" ] && continue
    set -- $(printf '%s' "$bounds" | tr -cs '0-9' ' ')
    l=$1; t=$2; r=$3; b=$4
    if [ "$x" -ge "$l" ] && [ "$x" -lt "$r" ] && [ "$y" -ge "$t" ] && [ "$y" -lt "$b" ]; then
      text=$(printf '%s' "$node" | grep -o 'text="[^"]\+"' | head -1 | sed 's/^text="//; s/"$//')
      [ -n "$text" ] && printf '%s' "$text" && return 0
    fi
  done < <(printf '%s' "$LAST_DUMP" | tr '>' '\n')
  return 1
}

# Land on the global Unread list from any state. Journeys 2-6 start here.
goto_list() {
  local _
  "$ADB" shell am force-stop "$APP_ID"
  "$ADB" shell monkey -p "$APP_ID" 1 >/dev/null 2>&1
  for _ in $(seq 1 20); do foreground_ok && break; sleep 1; done
  abort_if_background
  pause 5
  tap 81 297                     # drawer icon: no text anchor on Compose
  sleep 1.5
  tap 295 490                    # "Non lus" row: the label is duplicated in the
                                 # drawer (top-bar title + row), so no anchor tap
  sleep 1.5
  expect_screen "unread list" 'text="Non lus"'
}

# Sleep that keeps the screen awake: keyevent 224 (WAKE_UP — no tap, safe
# on an unlocked screen) resets the screen-off timer even when unplugged.
pause() {
  local left=$1
  while [ "$left" -gt 0 ]; do
    "$ADB" shell input keyevent 224 >/dev/null 2>&1
    [ "$left" -ge 2 ] && sleep 2 || sleep "$left"
    left=$((left - 2))
  done
}

# UI dump used for text anchors and for the drawer badge read.
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
  crashes=$("$ADB" logcat -d -s AndroidRuntime:E 2>/dev/null | grep -c "Process: $APP_ID" || true)
  if [ "$crashes" -gt 0 ]; then
    FAIL=$((FAIL + 1)); step "❌ FAIL: $crashes crash(es) of $APP_ID in logcat"
    "$ADB" logcat -d -s AndroidRuntime:E | grep -A2 "Process: $APP_ID" | tail -30 >> "$REPORT"
  else
    PASS=$((PASS + 1)); step "✅ PASS: no crash"
  fi
  # Always: a buffer that keeps old crashes is what produced the false FAIL of
  # 2026-10-11. Matching on the package (not on the PID) also catches a crash
  # followed by a restart, when pidof already returns the new process.
  logcat_reset
}

summary() {
  echo "" >> "$REPORT"
  echo "**$JOURNEY: $PASS passed, $FAIL failed (cumulative)**" >> "$REPORT"
  echo "" >> "$REPORT"
}
