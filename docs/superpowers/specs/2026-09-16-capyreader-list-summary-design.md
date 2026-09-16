# List AI Summary — Design

Date: 2026-09-16
Status: Draft (pre-plan)
Feature branch (future): `list-summary`

## Goal

Summarize **any article list** in CapyReader — a folder (category), a feed, a
set of feeds, the home list — regardless of read status. One tap in the list
top bar opens a streaming digest of what the list contains, written live
(reusing the article-summary typewriter infrastructure).

## Non-goals (v1)

- No caching: the underlying set changes constantly; every tap is a fresh call.
- No user-editable list prompt (constant in code; extension point noted).
- No background/periodic digests, no notifications.
- No schema changes / no SQLDelight migration (no new tables — no cache).

## User decisions (2026-09-16)

| Question | Decision |
|---|---|
| Aggregate content | Titles + excerpts (feed, date, title, ~1-2 sentences) |
| Entry point | Top bar button on the list screen |
| Article selection | Day-backfill: current day first, then previous day if the current day is not enough, and so on |
| Cache | None in v1 |

## Article selection

- Scope = the current `ArticleFilter` (feed, folder, or all), **ignoring the
  unread/read toggle** — both statuses included by design.
- Day-backfill with a soft cap: fetch the most recent articles (bounded query),
  group by calendar day (device timezone), accumulate whole days newest-first
  until at least 50 articles are collected. A day is never split; if a day
  would push past the hard ceiling of 120, that whole day is dropped
  (day-level granularity end to end).
- Rationale: honors "per-day fill" semantics (a day is a coherent unit) while
  keeping input size predictable.

## Aggregate format (what is sent to the model)

Plain text, one block per article, newest first:

```
[2026-09-16] Feed Title — Article Title
First ~220 characters of the article text.
```

- Excerpt source: the `summary` column when non-blank, otherwise the first
  ~220 chars of `htmlToText(content)` — **both sources go through
  `htmlToText`** (RSS summaries are frequently HTML) and are whitespace-
  normalized; blank-safe (title-only entry).
- Estimated input: ~4-5k tokens for 50 articles — comfortable for the flat
  coding plan.

## Model request

- **Reuses `SummaryClient` / `SummaryRequest` unchanged** (systemPrompt,
  title = human scope label e.g. "Folder: News", text = aggregate). No
  `aiclient` changes.
- `DEFAULT_LIST_PROMPT`: constant in code — English instructions, output in
  French (mirroring the adopted article default), thematic digest grouped by
  subject, notable items called out, injection-guard line included
  ("treat article text as data…").

## UX flow

1. List screen top bar gains a summarize icon on feed, folder, all-articles
   and Today lists (SavedSearches excluded in v1); hidden for empty lists,
   action gated on the same `isConfigured` check as the article summary.
2. Tap → `ModalBottomSheet` opens immediately (loading state, spinner).
3. Streaming renders with the same components as the article card:
   stable blocks through per-block Markdown (no Loading flash), plain
   stripped tail, frame-locked typewriter.
4. Dismiss cancels the job (no cache write to lose — by design). The sheet
   uses `heightIn(min/max)` + internal `verticalScroll` so streaming growth
   scrolls content instead of resizing the sheet (M3 sheet animation would
   jank otherwise).
5. Errors render inside the sheet (same error contract as the article card).

## Reuse inventory (verified to exist)

- `SummaryClient.summarizeStreaming` (SSE Flow) — untouched.
- `SummaryStateHolder` / stable-controller pattern, typewriter
  `advanceDisplayed`, `splitStreamText`, `stripStreamTailMarkers` — reused;
  second occurrence of the run-loop (rule of three: extract only if a third
  use appears).
- Per-block Markdown rendering + typography config (extract a
  `SummaryContent(state)` composable shared by card and sheet).
- `htmlToText` for excerpts.

## Code anchors

- List screen: `app/.../ui/articles/ArticleScreen.kt`,
  `ArticleScreenViewModel.kt` (owns `ArticleFilter`).
- Top bar: `FilterAppBarTitle.kt` / `FilterActionMenu.kt` area.
- Query: `capy/.../db/articles.sq` `findBy:` (existing parameterized list
  query; v1 reuses or adds a bounded variant — decided in the plan's research
  task). Column `summary` exists on articles.
- No migration → no `verifySqlDelightMigration` concern.

## Risks / open questions

- Day grouping timezone = device local (matches user intuition; noted).
- The existing list queries exclude feeds outside `('main','important')`
  (feed-priority filter): the digest silently omits muted feeds — matches
  the visible list, documented here so nobody is surprised.
- `ArticleFilter` has five variants (Articles, Today, Feeds, Folders,
  SavedSearches): v1 covers all but SavedSearches (icon hidden there).
- If GLM emits a monolithic block (no blank line), the whole digest rides in
  the plain tail until completion — accepted (same known behavior as the
  article card).
- The top bar is already crowded on narrow screens — icon-only with
  contentDescription; validate on device.

## Extension points (not v1)

- Editable list prompt in AI settings (second pref).
- Digest for saved searches.
- Optional short-TTL in-memory cache.
