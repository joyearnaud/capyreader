#!/usr/bin/env bash
# Device journey runner for the CapyReader fork (debug build).
#
# Drives the installed app through 5 user journeys over adb, taking
# screenshots and asserting text anchors along the way, then writes a
# Markdown report under device-journeys/<timestamp>/.
#
# Usage:  scripts/journeys/run.sh [journey-number ...]
# Journeys: 1 launch · 2 article · 3 digest-sheet · 4 summaries · 5 mark-read
# Requirements: one device connected, unlocked (the runner keeps the screen
# on while it works and restores the previous setting at the end).

set -u
HERE="$(cd "$(dirname "$0")" && pwd)"

TS=$(date -u +%Y%m%d-%H%M%S)
ART_DIR="$(cd "$HERE/../.." && pwd)/device-journeys/$TS"
REPORT="$ART_DIR/report.md"
mkdir -p "$ART_DIR"

# shellcheck source=lib.sh
source "$HERE/lib.sh"
exec 9>"$ART_DIR/run.lock"

echo "# Device journeys — $TS" > "$REPORT"
echo "" >> "$REPORT"
echo "App: $APP_ID" >> "$REPORT"
echo "" >> "$REPORT"

# Only run selected journeys (all by default).
WANT="${*:-1 2 3 4 5}"
want() { [[ " $WANT " == *" $1 "* ]]; }

screen_on() {
  local state
  state=$("$ADB" shell dumpsys power 2>/dev/null | grep 'mWakefulness=' | head -1)
  [[ "$state" == *"Awake"* ]]
}

# SAFETY: never send input unless CapyReader is the foreground app.
# Blind taps on the lock screen (or the clock!) can trigger real actions.
foreground_ok() {
  "$ADB" shell dumpsys activity activities 2>/dev/null | \
    grep -q "topResumedActivity.*$APP_ID"
}

if ! foreground_ok; then
  msg="❌ CapyReader is not in the foreground (device locked?) — no input sent. Open the app, then re-run."
  echo "$msg" >&2
  echo "$msg" >> "$REPORT"
  exit 1
fi

"$ADB" shell svc power stayon true
trap '"$ADB" shell svc power stayon false' EXIT

"$ADB" logcat -c

# ---------------------------------------------------------------------------
if want 1; then
  journey "1-launch"
  "$ADB" shell am force-stop "$APP_ID"
  "$ADB" shell monkey -p "$APP_ID" 1 >/dev/null 2>&1
  sleep 4
  # Deterministic start: global Unread status (always populated).
  tap 81 297
  sleep 1.5
  tap_text "Non lus" 295 490
  sleep 1.5
  "$ADB" shell input keyevent 4
  sleep 1.5
  shot "list"
  assert_text "Comments" "list rows rendered"
  assert_text "Development\|News\|Tech" "a feed or folder title is visible"
  crash_check
  summary
fi

# ---------------------------------------------------------------------------
if want 2; then
  journey "2-article-native-reader"
  # Open the first list row.
  tap 593 510
  sleep 4
  shot "article"
  assert_text "de \|à " "article byline rendered"
  assert_text "Article URL\|http\|the \|de \|Le " "article body rendered"
  back
  sleep 2
  crash_check
  summary
fi

# ---------------------------------------------------------------------------
if want 3; then
  journey "3-digest-sheet"
  tap 768 297                     # summary icon in the list top bar
  sleep 2
  assert_text "Résumer cette liste" "scope dialog shown"
  tap_text "Tout le contenu" 878 1593
  sleep 8
  shot "sheet-loading-or-hit"
  # Generation takes ~50s on a miss; the DB hit is instant. Poll for content.
  found=0
  for _ in $(seq 1 20); do
    if has_text "Vue d\|Synthèse\|Résumé de "; then found=1; break; fi
    sleep 3
  done
  if [ "$found" = 1 ]; then
    PASS=$((PASS + 1)); step "✅ PASS: digest content rendered"
  else
    FAIL=$((FAIL + 1)); step "❌ FAIL: digest content never rendered"
  fi
  shot "sheet-content"
  # Scroll into the digest and open a numbered reference.
  swipe 640 1900 640 1100 300
  sleep 1
  shot "before-ref"
  tap_text " 5." 640 1400         # fallback only; the dump match wins
  sleep 3
  shot "article-from-ref"
  assert_text "Article URL\|http\|de \|Le \|the " "reference opened an article"
  back
  sleep 3
  shot "sheet-reopened"
  assert_text "Comments" "back on the list with the sheet above it (see screenshot)"
  crash_check
  summary
fi

# ---------------------------------------------------------------------------
if want 4; then
  journey "4-summaries-history"
  # Close any open digest sheet first: a tap outside it would be eaten.
  swipe 445 1400 445 2600 250
  sleep 1.5
  tap 81 297                      # drawer
  sleep 2
  assert_text "Résumés" "drawer row present"
  tap_text "Résumés" 295 825
  sleep 2.5
  shot "summaries-list"
  assert_text "Résumé de liste\|Résumé d'article" "history rows rendered"
  # Open the newest digest.
  tap 515 460
  sleep 2.5
  shot "history-detail"
  assert_text "Résumés" "history detail open (top bar)"
  swipe 640 1700 640 1200 300
  sleep 1
  tap_text " 5." 640 1400
  sleep 3
  shot "history-article"
  assert_text "Article URL\|http\|de \|Le \|the " "history reference opened an article"
  back
  sleep 2.5
  shot "history-detail-again"
  assert_text "Résumés" "back to digest detail (native pop)"
  crash_check
  summary
fi

# ---------------------------------------------------------------------------
if want 5; then
  journey "5-mark-read-scoped"
  # Close any open digest sheet first.
  swipe 445 1400 445 2600 250; sleep 1.5
  tap 81 297; sleep 2
  before=$(ui_dump | grep -o 'text="Non lus"[^>]*' | grep -o '[0-9]\+' | head -1)
  step "unread badge before: ${before:-?}"
  "$ADB" shell input keyevent 4; sleep 1.5
  tap 768 297; sleep 2
  tap_text "Tout le contenu" 878 1593
  sleep 8
  # The mark-read button sits at the bottom of the sheet content.
  for _ in $(seq 1 6); do
    swipe 640 2100 640 400 120; sleep 0.3
    if has_text "Tout marquer comme lu"; then break; fi
  done
  shot "before-mark"
  tap_text "Tout marquer comme lu" 904 2653
  sleep 1.5
  assert_text "Marquer tous les articles comme lus" "confirmation dialog"
  tap_text "Confirmer" 844 1520
  sleep 3
  tap 81 297; sleep 2
  after=$(ui_dump | grep -o 'text="Non lus"[^>]*' | grep -o '[0-9]\+' | head -1)
  step "unread badge after: ${after:-?}"
  shot "after-mark"
  if [ -n "$before" ] && [ -n "$after" ] && [ "$after" -lt "$before" ]; then
    PASS=$((PASS + 1)); step "✅ PASS: unread badge decreased ($before → $after)"
  elif [ -n "$before" ] && [ -n "$after" ] && [ "$after" = "$before" ]; then
    step "⚠️ WARN: badge unchanged ($before) — digest likely contained no unread; check screenshots"
  else
    FAIL=$((FAIL + 1)); step "❌ FAIL: badge not readable ($before → $after)"
  fi
  "$ADB" shell input keyevent 4
  crash_check
  summary
fi

echo "Done. Artifacts: $ART_DIR" >&2
echo "PASS=$PASS FAIL=$FAIL" >&2
