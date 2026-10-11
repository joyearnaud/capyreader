# Plan — harden the device journey harness (`scripts/journeys/`)

Why: the 2026-10-11 gate run (`device-journeys/20261011-185006/`) came back `PASS=5 FAIL=2`
but was **not trustworthy either way**. Journey 1 failed on stale logcat from a system app;
journeys 5 and 6 walked into the reader instead of the list and still "passed" (a PASS only
means "no crash"). Fix the harness before promoting `reconcile/2026-10-08` into `integration`.

Authority: docs/fork/maintenance-strategy.md §6 (the harness asserts almost nothing;
screenshots are the artifact) and the oracle verdict of 2026-10-11.

## Steps

1. `lib.sh` — stop the logcat false positive: read crashes for the app PID only
   (`adb shell pidof $APP_ID` + `logcat -d --pid`), and clear the buffer at run start *and*
   after every check, not only after a failure.
2. `lib.sh` — add `expect_screen <label> <pattern>`: dump the UI, assert the pattern is present,
   and on failure record the journey as FAILED and stop the run. A wrong screen invalidates
   everything downstream (§6: "a run that aborts proves nothing").
3. `lib.sh` — add `tap_text <label> <pattern>`: tap the node bounds found in the UI dump instead
   of hardcoded pixels; fall back to the old coordinates only when the anchor is absent. The
   screens move on every upstream merge — anchors survive, pixels do not.
4. `run.sh` — give every journey a deterministic entry state: force-stop, relaunch and return to
   the "Non lus" list before it starts. Today journeys inherit whatever the previous one left
   behind, which is how 5 and 6 drifted.
5. `run.sh` — assert the screen after each navigation step in journeys 3, 4, 5 and 6, and replace
   the fragile coordinate taps (drawer close, sheet buttons) with `tap_text`.
6. `run.sh` — in journey 5, read the badge while the drawer is proven open and reset with a
   relaunch instead of the blind "tap the scrim at 1150,1500" that opened an article.
7. Run the gate on the phone (build + install + `scripts/journeys/run.sh`) and **read every
   screenshot**; a journey is only green if its screenshots show its intended screens.
8. Reviewer over the harness diff.

## Exit

Green: journeys 1–6 land on their intended screens, no false-positive crash, journey 5 reads a
real badge delta. Then step 5 of the reconcile plan (`ff-only` into `integration`) becomes legal.
Red: report the failing journey with its screenshot path; the scratch branch is still the exit.
