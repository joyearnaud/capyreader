# List AI Summary — Implementation Plan

Date: 2026-09-16
Spec: `docs/superpowers/specs/2026-09-16-capyreader-list-summary-design.md`
Branch: `list-summary` (from `ai-summary` once M1 review is done)
Workflow: SDD (subagent-driven), ledger
`.superpowers/sdd/2026-09-16-capyreader-list-summary/progress.md`

## Task 0 — Research & anchors (read-only)

Verify and record exact signatures (ledger notes):

- **List queries are NOT `findBy:`** (that one is by-ID, `LIMIT 1`). The
  anchors are `articlesByStatus.sq`, `articlesByFeed.sq`,
  `articlesBySavedSearch.sq` — each with `allNewestFirst:`
  (ORDER BY published_at DESC, LIMIT/OFFSET) selecting title, summary,
  published_at, feed_title. Confirm parameter shapes, especially the
  read-status pattern `(:read IS NULL OR …)` — passing `read = null`
  includes both statuses natively.
- Folder scope → feed-set path: `Account.kt` `ArticleFilter.Feeds` handling
  (~line 187) — confirm how folder filters resolve to feed IDs.
- Confirm the feed-priority restriction `('main','important')` in those
  queries (documented spec note).
- `ArticleFilter` variants and where `ArticlesScreenViewModel` /
  `ArticleScreen.kt` compose the top bar (`FilterAppBarTitle.kt`,
  `FilterActionMenu.kt`).
- `htmlToText` (aiclient) — signature + blank-content edge.

## Task 1 — Selection: day-backfill selector (TDD, :app unit tests)

New pure function in `app/.../ui/articles/summary/DigestSelector.kt`:

```
selectDigestDays(candidates, softCap = 50, hardCap = 120): DigestSelection
```

- Input: entries (id, feedTitle, date, title, excerpt) sorted newest-first.
- Group by calendar day (device timezone, `java.time.LocalDate`), accumulate
  whole days newest-first until count ≥ softCap; never split a day; if a day
  would push past hardCap, drop that whole day and stop.
- Output: selected entries + coverage label (oldest → newest date, count).

Tests: soft-cap boundary (day not split), hard-cap whole-day drop,
single-day list, empty list. Red → green.

## Task 2 — Excerpt extraction (TDD)

`buildDigestEntry(article)`: excerpt = `htmlToText(summary)` when non-blank,
else first ~220 chars of `htmlToText(content)`; whitespace-normalized;
blank-safe (title-only entry). Tests: summary preferred (HTML stripped),
content fallback, empty content → title-only.

## Task 3 — Data access (likely NO new SQL)

Wrap the existing `allNewestFirst` queries behind an `Account`/records
function `findRecentForDigest(filter, limit = 200)` passing `read = null`
(both statuses). Only add SQL if a variant genuinely cannot serve. Tests on
InMemoryDatabaseProvider: scope filtering, ordering, limit, read+unread mix.
No schema change — no migration, `verifySqlDelightMigration` unaffected.

## Task 4 — Aggregate + request builder (TDD)

Construct `SummaryRequest` **directly** (do NOT use `buildSummaryRequest` —
it is article-specific: truncation, contentHTML):

- `title` = scope label ("Folder: News" / "Feed: X" / "All articles" /
  "Today").
- `text` = coverage line + one block per article
  (`[2026-09-16] Feed — Title\nexcerpt`).
- `systemPrompt` = `DEFAULT_LIST_PROMPT`, placed in
  `AppPreferences.AiOptions` companion beside `DEFAULT_PROMPT` (constant,
  English instructions, French output, injection-guard line).

Tests: block format, scope label mapping, coverage line.

## Task 5 — Streaming controller (reuse, minimal duplication)

`rememberListSummary(filter, ...)` beside `rememberSummary`:

- Same `SummaryStateHolder` stable-controller pattern; same collector-owns-
  failures discipline; NO cache lookup/upsert (no cache); frame-locked
  typewriter loop reusing `advanceDisplayed`.
- Holder remembered per `ArticleFilter` identity (no `article?.id` keying).
- Guard: list non-empty + `isConfigured`.

Ledger note: run-loop duplication is occurrence 2; extract a shared runner
only when a third use appears.

## Task 6 — UI: shared body + sheet + top bar icon

- Extract `SummaryContent(state)` (per-block Markdown — keep the split and
  per-block calls together, they are the Loading-flash fix — plain stripped
  tail, error text) from `SummaryCard`; `markdownTypography` stays inside
  the composable (@Composable). Reuse inside `ListSummarySheet`.
- `ListSummarySheet`: ModalBottomSheet with `heightIn(min/max)` + internal
  `verticalScroll` (growth scrolls content, never resizes the sheet),
  loading spinner row, close action. No Re-summarize button in v1 (dismiss
  + tap again) — revisit on device.
- Top bar icon on the list screen: shown for Articles / Today / Feeds /
  Folders filters, hidden for SavedSearches and empty lists; enabled when
  configured. Icon-only + contentDescription; strings en + fr.

## Task 7 — Build, install, device acceptance

- `:app:testFreeDebugUnitTest :app:assembleFreeDebug` (+ `:capy:test` if
  Task 3 touched capy), install on Pixel.
- Device checks: icon on home/folder/feed lists (absent on saved searches);
  sheet streams smoothly WITHOUT resizing; day-backfill includes read+unread;
  muted feeds absent from the digest; dismiss cancels; airplane mode → error
  card; talkback pass.

## Task 8 — Review & wrap

- Whole-diff review when the reviewer lane is available; spec amendments
  discovered during work; ledger close; memory_save for the fork.
