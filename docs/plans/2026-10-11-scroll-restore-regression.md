# Plan — fix the lost list position when returning from an article

Symptom: on `reconcile/2026-10-08`, scrolling deep in the Unread list, opening an article and
pressing back does not land on the tapped row. Reproduced 2/2 by the hardened journey 6
(`device-journeys/20261011-200912/`, `…-201056/`).

Authority: fork commit `75c485e4` ("returning from an article restores the list at the tapped
position") is the intent. Oracle verdict of 2026-10-11 confirms the diagnosis and the patch shape.

## Root cause

`LazyPagingItems.rememberLazyListState()` (upstream, `LazyListStateExt.kt`) hands out a throwaway
`LazyListState(0, 0)` while `itemCount == 0`, and the real instance only after a recomposition with
items. The restore in `ArticleScreen.kt` is keyed on `selectedArticleID` alone, so it captures the
throwaway instance, waits for `itemCount > 0`, then scrolls a state nothing lays out — and consumes
`lastReadArticleID`/`lastListScrollPosition` on the way, losing the position for good. What is left
is nav3's stale saved index, hence "top of the list" in one run and "one row off" in the other.

## Steps

1. Key the effect on the list state too (`LaunchedEffect(selectedArticleID, listState)`), return
   early while `articles.itemCount == 0`, and keep the reason in a comment so the next merge does
   not re-litigate it. Drop the now-unused `withFrameNanos` import.
2. Add temporary `CapyLog.debug("list_restore", …)` lines (index, itemCount, instance identity) to
   attribute the landing position, and check `mark_read_on_scroll` in logcat: that effect calls
   `scrollToItem(0)` on every emission of `totalItemsCount` and would mask a fix.
3. Build, install, run `scripts/journeys/run.sh 6` — twice, since the failure was 2/2 before.
4. Strip the temporary instrumentation. One `CapyLog.debug("list_restore", …)` stays at the
   decision point, following the `mark_read_on_scroll:collect` precedent in the same file; then
   rebuild and run the full suite (1–6) and read every screenshot.
5. Reviewer over the diff.
6. Only then continue the reconcile plan: `git merge --ff-only` into `integration`, tag
   `phone-2026-10-11`, push, delete the scratch branch.

## Exit

Green: journey 6 reports a Δ within tolerance on two consecutive runs and the full suite lands every
journey on its intended screen. Red: the fix masks a different scroller — logcat attribution above
says which one, and that one is then the real target.
