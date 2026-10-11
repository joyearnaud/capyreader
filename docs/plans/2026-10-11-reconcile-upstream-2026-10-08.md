# Plan — validate and promote `reconcile/2026-10-08` into `integration`

Authority: `docs/fork/maintenance-strategy.md` §4.3 (runbook "Reconcile with upstream").
Branch under test: `reconcile/2026-10-08` @ `962bd173` (upstream merge already resolved, tree clean).
Upstream local ref `upstream/main` @ `b4b47c10` — re-fetch before trusting (§4.1).

## Steps

1. Gate build — `mise exec java@zulu-21.34.19.0 -- ./gradlew -Dorg.gradle.jvmargs="-Xmx4g -Dfile.encoding=UTF-8" -Dkotlin.daemon.jvmargs="-Xmx3g" :app:testFreeDebugUnitTest :app:assembleFreeDebug`
2. Install on device — `adb install -r app/build/outputs/apk/free/debug/*.apk`
3. Device journeys — `scripts/journeys/run.sh` (1 launch · 2 article · 3 digest · 4 summaries · 5 mark-read)
4. Read the screenshots of the run. They are the validation artifact; a run that aborts proves nothing (§6).
5. Promote only if green — `git checkout integration && git merge --ff-only reconcile/2026-10-08`
6. Tag and publish — `git tag -a phone-2026-10-11` then `git push origin integration --tags`
7. Delete the scratch branch `reconcile/2026-10-08`
8. Reviewer over the promotion (post-merge verification)

## Exit

Red gate → leave `integration` untouched, delete nothing, report the failing journey and the
screenshot path. Nothing is lost: the scratch branch is the exit (§4.3).

---

## Result — 2026-10-11 (RED, not promoted)

Steps 1–4 done, step 5 stopped. Artifact: `device-journeys/20261011-185006/` (19 PNG + report.md).
Build `BUILD SUCCESSFUL`, install OK, journeys `PASS=5 FAIL=2`.

| Finding | Verdict |
| --- | --- |
| `1-launch` FAIL: 6 `FATAL EXCEPTION` | Harness false positive. `lib.sh crash_check` greps the whole logcat ring buffer with no PID filter and clears it only *after* a failure; the entries are dated 10-06/10-09 and belong to the system app `com.android.companiondevicemanager`. `1-launch-list.png` shows a healthy list. |
| `5-mark-read-scoped` FAIL: badge unreadable | Real. Its three screenshots show an **article in the reader**, not the drawer/sheet — the taps drifted off-path. |
| `6-list-scroll-restore` PASS | Meaningless. Its final screenshot also shows the reader, not the restored list. `PASS` only means "no crash". |

The harness's only behavioural assertion (journey-5 badge delta) is therefore unproven on this
build. Oracle cross-checked run `device-journeys/20260923-154746/` (pre-merge): journey 5 then ran
its whole scenario (badge 168 → 168), so the drift is *caused by the merge* — drawer layout moved,
or Nav 3 changes the state journey 4 leaves behind. Calibration drift vs a real fork regression is
**not settled**. Oracle verdict: do not promote.

Next: fix the harness on this reconcile line (`adb logcat -c` at start + PID filter in
`crash_check`; assert the expected screen before each tap, and abort instead of pressing on),
re-run, re-read the screenshots, then step 5.


---

## Outcome — 2026-10-11, later the same evening: PROMOTED

The red gate was not the end. Two real bugs were found and fixed on the scratch branch, then the
scratch branch was fast-forwarded into `integration` (`bbac39ab`) and tagged `phone-2026-10-11`.

| What was wrong | Fix |
| --- | --- |
| `crash_check` counted FATAL lines from the whole logcat buffer and cleared it only after a failure | match on the app's package, clear at run start and after every check |
| Journeys inherited the previous journey's screen; journeys 5 and 6 walked into the reader | every journey resets with `goto_list`, and anchors replace the pixels the merge invalidated |
| Journey 5 tapped the summary icon at 768,297 — the merge had moved it to 908,300 | `content-desc="Résumer cette liste"` |
| The reader round-trip lost the list position: the restore scrolled a throwaway `LazyListState` and `MarkReadOnScrollEffect` reset to the top | key the effect on the list state, and `drop(1)` the re-emitted item count |

Final suite: 1–6 green (`device-journeys/20261011-210136/`), screenshots read. Journey 5's badge
delta stayed at 192 → 192 because the digest held nothing unread on this device; the journey reports
that as a WARN, so the scoped mark-read path is exercised but its badge decrease is not proven yet.
