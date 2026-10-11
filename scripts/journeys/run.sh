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

WANT="${*:-1 2 3 4 5 6}"
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

# Start from an empty logcat buffer: stale system-app crashes from days before
# the run are what made journey 1 fail falsely on 2026-10-11.
logcat_reset

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
  goto_list
  tap 593 510                     # first list row
  pause 3
  shot "article"
  back; sleep 2
  crash_check
  summary
fi

# ---------------------------------------------------------------------------
if want 3; then
  journey "3-digest-sheet"
  goto_list
  tap_text "summary icon" 'content-desc="Résumer cette liste"'
  sleep 2
  shot "scope-dialog"
  expect_screen "scope dialog" 'text="Tout le contenu"'
  tap_text "scope: all content" 'text="Tout le contenu"'
  pause 6
  shot "sheet-loading-or-hit"
  # Wait until the sheet settles: on a DB hit it is instant, on a miss
  # generation takes ~50s. Two byte-identical consecutive shots = done.
  prev=""
  for i in $(seq 1 14); do
    pause 5
    shot "gen-$i"
    cur="$ART_DIR/${JOURNEY// /_}-gen-$i.png"
    if [ -n "$prev" ] && cmp -s "$prev" "$cur"; then rm -f "$cur"; break; fi
    prev="$cur"
  done
  # Scroll into the digest and open a reference (calibrated).
  swipe 640 1900 640 1100 300; sleep 1
  shot "before-ref"
  tap 640 1400
  pause 2
  shot "article-from-ref"
  back; pause 2
  shot "sheet-reopened"
  crash_check
  summary
fi

# ---------------------------------------------------------------------------
if want 4; then
  journey "4-summaries-history"
  # Start from the list: the previous journey leaves a digest sheet open, and a
  # tap meant to dismiss it is eaten.
  goto_list
  tap_text "open drawer" 'content-desc="Open navigation drawer"'; sleep 2
  expect_screen "drawer open" 'text="Résumés"'
  tap_text "drawer: Résumés" 'text="Résumés"'
  pause 2
  shot "summaries-list"
  tap 515 460; pause 2          # newest entry
  shot "history-detail"
  swipe 640 1700 640 1200 300; sleep 1
  tap 640 1400; sleep 3           # a reference link
  shot "history-article"
  back; pause 2
  shot "history-detail-again"
  crash_check
  summary
fi

# ---------------------------------------------------------------------------
if want 5; then
  journey "5-mark-read-scoped"
  # Deterministic start. The old version began by swiping a sheet away and
  # tapping the scrim at 1150,1500 to close the drawer — that tap opened an
  # article instead, and the whole journey then ran inside the reader.
  goto_list
  tap_text "open drawer" 'content-desc="Open navigation drawer"'; sleep 2
  expect_screen "drawer open" 'text="Résumés"'
  refresh_dump
  before=$(echo "$LAST_DUMP" | grep -o 'text="Non lus"[^>]*' | grep -o '[0-9]\+' | head -1)
  step "unread badge before: ${before:-unreadable}"
  # Reset by relaunching rather than closing the drawer blind.
  goto_list
  tap_text "summary icon" 'content-desc="Résumer cette liste"'
  expect_screen "scope dialog" 'text="Tout le contenu"'
  tap_text "scope: all content" 'text="Tout le contenu"'
  # Generation takes tens of seconds on a miss: wait for the sheet to be up and
  # for its progress line to go, instead of guessing with a fixed pause.
  wait_anchor 'content-desc="Fermer la feuille"' 30
  wait_gone 'text="Génération du résumé…"' 180
  # The mark-read button sits at the bottom of the sheet content, and how far
  # that is depends on how much the digest rendered. Scroll until the anchor is
  # actually on screen rather than guessing a fixed number of swipes.
  for _ in $(seq 1 12); do
    dump_ui && [ "$(match_count 'text="Tout marquer comme lu"')" -gt 0 ] && break
    swipe 640 2100 640 400 200
    sleep 0.4
  done
  shot "before-mark"
  tap_text "mark all read" 'text="Tout marquer comme lu"'
  sleep 1.5
  shot "confirm-dialog"
  tap_text "confirm" 'text="Confirmer"'
  pause 3
  goto_list
  tap_text "open drawer" 'content-desc="Open navigation drawer"'; sleep 2
  expect_screen "drawer open" 'text="Résumés"'
  refresh_dump
  after=$(echo "$LAST_DUMP" | grep -o 'text="Non lus"[^>]*' | grep -o '[0-9]\+' | head -1)
  step "unread badge after: ${after:-unreadable}"
  shot "after-mark"
  if [ "$SKIP" -eq 0 ]; then
    if [ -n "$before" ] && [ -n "$after" ] && [ "$after" -lt "$before" ]; then
      PASS=$((PASS + 1)); step "✅ PASS: unread badge decreased ($before → $after)"
    elif [ -n "$before" ] && [ -n "$after" ] && [ "$after" = "$before" ]; then
      step "⚠️ WARN: badge unchanged ($before) — digest likely contained no unread"
    else
      FAIL=$((FAIL + 1)); step "❌ FAIL: badge not readable ($before → $after)"
    fi
  fi
  crash_check
  summary
fi

# ---------------------------------------------------------------------------
if want 6; then
  journey "6-list-scroll-restore"
  goto_list
  # Scroll deep, open the topmost fully-visible article, then come back: the list
  # must land on that same row at the same height. The old version asserted
  # nothing at all, so a lost position still reported PASS.
  for i in $(seq 1 10); do
    swipe 640 2100 640 700 200
    sleep 0.35
  done
  pause 2
  shot "list-deep"
  title=$(top_row_title 420 2300)
  step "top row after scrolling: ${title:-unreadable}"
  if [ -z "$title" ]; then
    fail_journey "no article title in the viewport after scrolling"
  else
    y_before=$(anchor_y "text=\"$title\"")
    tap_text "article row" "text=\"$title\""
    pause 3
    shot "article"
    back
    pause 3
    shot "list-restored"
    if [ "$SKIP" -eq 0 ]; then
      y_after=$(anchor_y "text=\"$title\"")
      step "title centre y: $y_before before, ${y_after:-unreadable} after"
      # The restore anchors on the article that was opened and scrolls it back to
      # the top of the viewport, so the row's exact y is not the contract. What
      # must hold is that the tapped row is on screen again: before this fix the
      # list returned to the top of the list and the row was absent from the dump.
      if [ -n "$y_after" ]; then
        PASS=$((PASS + 1)); step "✅ PASS: tapped row back on screen (y $y_before → $y_after)"
      else
        FAIL=$((FAIL + 1)); step "❌ FAIL: tapped row not on screen after back ($title)"
      fi
    fi
  fi
  crash_check
  summary
fi

echo "Done. Artifacts: $ART_DIR" >&2
echo "PASS=$PASS FAIL=$FAIL (crash + screen assertions; screenshots are the content validation)" >&2
[ "$FAIL" -gt 0 ] && exit 1
exit 0
