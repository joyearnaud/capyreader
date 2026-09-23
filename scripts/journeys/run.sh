#!/usr/bin/env bash
# Device journey runner for the CapyReader fork (debug build).
#
# Drives the installed app through 5 user journeys over adb and takes a
# screenshot at every checkpoint. Screenshots are the validation artifact;
# the only automated PASS/FAIL signals are app crashes (logcat) and the
# unread-badge delta in journey 5 — uiautomator dumps proved unreliable on
# Compose windows, so they are not used for assertions.
#
# Usage:  scripts/journeys/run.sh [journey-number ...]
# Journeys: 1 launch · 2 article · 3 digest-sheet · 4 summaries · 5 mark-read
# Requirements: one device connected, unlocked, with CapyReader in the
# foreground (the runner refuses to send any input otherwise, and re-checks
# before every action).

set -u
HERE="$(cd "$(dirname "$0")" && pwd)"

TS=$(date -u +%Y%m%d-%H%M%S)
ART_DIR="$(cd "$HERE/../.." && pwd)/device-journeys/$TS"
REPORT="$ART_DIR/report.md"
mkdir -p "$ART_DIR"

# shellcheck source=lib.sh
source "$HERE/lib.sh"

echo "# Device journeys — $TS" > "$REPORT"
echo "" >> "$REPORT"
echo "App: $APP_ID" >> "$REPORT"
echo "" >> "$REPORT"

WANT="${*:-1 2 3 4 5}"
want() { [[ " $WANT " == *" $1 "* ]]; }

# SAFETY: never send input unless CapyReader is the foreground app.
if ! foreground_ok; then
  msg="❌ CapyReader is not in the foreground — no input sent. Open the app, then re-run."
  echo "$msg" >&2
  echo "$msg" >> "$REPORT"
  exit 1
fi

"$ADB" shell svc power stayon true
trap '"$ADB" shell svc power stayon false' EXIT

# ---------------------------------------------------------------------------
if want 1; then
  journey "1-launch"
  "$ADB" shell am force-stop "$APP_ID"
  "$ADB" shell monkey -p "$APP_ID" 1 >/dev/null 2>&1
  # Wait for the app to reach the foreground before any input.
  for _ in $(seq 1 20); do foreground_ok && break; sleep 1; done
  abort_if_background
  sleep 4
  # Deterministic start: global Unread status (always populated).
  tap 81 297; sleep 1.5
  tap 295 490; sleep 1.5          # "Non lus" in the drawer
  shot "list"
  crash_check
  summary
fi

# ---------------------------------------------------------------------------
if want 2; then
  journey "2-article-native-reader"
  tap 593 510                     # first list row
  sleep 3.5
  shot "article"
  back; sleep 2
  crash_check
  summary
fi

# ---------------------------------------------------------------------------
if want 3; then
  journey "3-digest-sheet"
  tap 768 297                     # summary icon in the list top bar
  sleep 2
  shot "scope-dialog"
  tap 878 1593                    # "Tout le contenu"
  sleep 8
  shot "sheet-loading-or-hit"
  # Generation on a miss takes ~50s; poll screenshots until it settles.
  for i in 1 2 3 4 5 6; do
    sleep 8
    shot "gen-$i"
  done
  # Scroll into the digest and open a reference (calibrated).
  swipe 640 1900 640 1100 300; sleep 1
  shot "before-ref"
  tap 640 1400
  sleep 3
  shot "article-from-ref"
  back; sleep 3
  shot "sheet-reopened"
  crash_check
  summary
fi

# ---------------------------------------------------------------------------
if want 4; then
  journey "4-summaries-history"
  # Close any open digest sheet first: a tap outside it would be eaten.
  swipe 445 1400 445 2600 250; sleep 1.5
  tap 81 297; sleep 2             # drawer
  tap 295 825; sleep 2.5          # "Résumés" row
  shot "summaries-list"
  tap 515 460; sleep 2.5          # newest entry
  shot "history-detail"
  swipe 640 1700 640 1200 300; sleep 1
  tap 640 1400; sleep 3           # a reference link
  shot "history-article"
  back; sleep 2.5
  shot "history-detail-again"
  crash_check
  summary
fi

# ---------------------------------------------------------------------------
if want 5; then
  journey "5-mark-read-scoped"
  # Close any open digest sheet first.
  swipe 445 1400 445 2600 250; sleep 1.5
  tap 81 297; sleep 2             # drawer
  refresh_dump
  before=$(echo "$LAST_DUMP" | grep -o 'text="Non lus"[^>]*' | grep -o '[0-9]\+' | head -1)
  step "unread badge before: ${before:-unreadable}"
  tap 1150 1500; sleep 1.5        # scrim closes the drawer (no back!)
  tap 768 297; sleep 2            # summary icon
  tap 878 1593; sleep 8           # "Tout le contenu"
  # The mark-read button sits at the bottom of the sheet content.
  for _ in $(seq 1 6); do
    swipe 640 2100 640 400 120; sleep 0.3
  done
  shot "before-mark"
  tap 904 2653                    # "Tout marquer comme lu"
  sleep 1.5
  shot "confirm-dialog"
  tap 844 1520                    # "Confirmer"
  sleep 3
  tap 81 297; sleep 2             # drawer
  refresh_dump
  after=$(echo "$LAST_DUMP" | grep -o 'text="Non lus"[^>]*' | grep -o '[0-9]\+' | head -1)
  step "unread badge after: ${after:-unreadable}"
  shot "after-mark"
  if [ -n "$before" ] && [ -n "$after" ] && [ "$after" -lt "$before" ]; then
    PASS=$((PASS + 1)); step "✅ PASS: unread badge decreased ($before → $after)"
  elif [ -n "$before" ] && [ -n "$after" ] && [ "$after" = "$before" ]; then
    step "⚠️ WARN: badge unchanged ($before) — digest likely contained no unread"
  else
    FAIL=$((FAIL + 1)); step "❌ FAIL: badge not readable ($before → $after)"
  fi
  crash_check
  summary
fi

echo "Done. Artifacts: $ART_DIR" >&2
echo "PASS=$PASS FAIL=$FAIL (crash + badge checks; screenshots are the content validation)" >&2
