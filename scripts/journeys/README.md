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
| 6 | list-scroll-restore | Scroll deep, open an article, come back: the tapped row is back on screen. It also exercises the `markReadOnScroll` = ON path, the harder one. |

Artifacts land in `device-journeys/<UTC timestamp>/`: one folder of
screenshots per journey plus `report.md` with PASS/FAIL lines. Review the
screenshots after any structural change — diffs in layout or content are
the signal that something regressed.

## Calibration

Taps are calibrated for a 1280×2856 Pixel-class phone. **Prefer an anchor over a pixel**:
`tap_text <label> <anchor>` finds the node carrying an exact attribute match (`text="Résumés"`)
in the UI dump and taps its centre. Anchors survive the layout shifts that come with every upstream
merge, where hardcoded pixels do not. An anchor that matches zero nodes, or more than one, fails
the journey — never a coin flip, and there is no silent pixel fallback to hide a moved screen.
Anchors are still a form of calibration: they are the app's own labels, so a renamed string means a
recalibration, not a regression.

Icon-only controls (the drawer hamburger, the summary icon) keep pixel taps. Every pixel tap is
followed by an `expect_screen` assertion so a wrong screen surfaces immediately instead of cascading
into the next journey.

Fallback coordinates live inline in `run.sh`.

## Failure policy

- Every journey resets to a known screen first (`goto_list` = force-stop, relaunch, global Unread
  list). Journeys no longer inherit the state the previous one left behind.
- A failed assertion marks that journey FAILED and suppresses its later input; the run **continues**
  with the next journey, which resets its own state. One bad screen no longer hides the other five.
- A dump that comes back empty is reported as a *dump failure*, distinct from a missing anchor — a
  flaky dump must not read as a layout regression.
- `crash_check` reads only FATAL lines naming `com.capyreader.app.debug` (matching on the package,
  not the PID, so a crash followed by a restart is still caught) and clears the logcat buffer at
  run start and after every check.
- `run.sh` exits non-zero when any journey failed, so a red gate is machine-checkable.
- The screenshots remain the artifact: `PASS` only ever means "no crash on this screen".

## Device-state dependencies

Journey 6 needs the app's `markReadOnScroll` preference to be ON to cover the harder path: with it
ON, `MarkReadOnScrollEffect` resets the list to the top whenever `totalItemsCount` changes, and a
reader round-trip re-emits that count. The harness does not pin the preference — it reports what the
installed build does with the settings on the phone.
