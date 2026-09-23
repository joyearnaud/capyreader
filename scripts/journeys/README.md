# Device journeys

Integration-style journeys driven over adb against the installed debug
build (`com.capyreader.app.debug`). Each journey takes screenshots and
asserts text anchors from `uiautomator` dumps, then writes a Markdown
report. Re-run after every upstream sync or feature change to validate.

## Usage

```sh
scripts/journeys/run.sh            # all 5 journeys
scripts/journeys/run.sh 1 3        # subset
```

Requirements:
- One device connected over adb, **unlocked** (the runner keeps the screen
  on via `svc power stayon` and restores it at the end).
- The app is signed in (real account) — journeys exercise real data.

## Journeys

| # | Name | What it proves |
|---|------|----------------|
| 1 | launch | App starts without crash, list rows render |
| 2 | article | Native reader renders an article |
| 3 | digest-sheet | Scope dialog → generation/DB hit → sheet; reference → article → back → sheet restored |
| 4 | summaries | Drawer → history list → digest detail → reference → native back |
| 5 | mark-read-scoped | "Tout marquer comme lu" from the digest only lowers the unread badge by the digest's unread |
| 6 | list-scroll-restore | Scroll deep, open an article, come back: the list lands on the same article |

Artifacts land in `device-journeys/<UTC timestamp>/`: one folder of
screenshots per journey plus `report.md` with PASS/FAIL lines. Review the
screenshots after any structural change — diffs in layout or content are
the signal that something regressed.

## Calibration

Taps are calibrated for a 1280×2856 Pixel-class phone. When a text anchor
is found in the UI dump, the runner taps the node's bounds instead — so
most steps survive moderate layout shifts. Fallback coordinates live
inline in `run.sh`.
