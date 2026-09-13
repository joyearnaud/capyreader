# On-demand AI article summary — design

- **Date**: 2026-09-13
- **Status**: approved design, not yet implemented
- **Branch**: `ai-summary` (personal fork `joyearnaud/capyreader`, upstream `jocmp/capyreader`)
- **Scope**: personal use, Android only, no distribution

## 1. Goal

Add an on-demand **"Summarize"** action to the article screen: it sends the text of the article
currently on screen to an OpenAI-compatible provider (DeepSeek, Z.ai GLM, or any compatible
endpoint) using a key configured on the device, and renders the result in a Compose card next to
the article.

Success criteria:

- The summary covers the article **as displayed** (feed content or full-content extraction),
  typically arrives in under 10 s, and is written in French by default.
- Provider, endpoint, model and prompt are configurable at runtime — no rebuild to change them.
- Opening the same article again costs **zero API calls** and works offline.
- Article text goes only to the configured provider; the API key never enters the repo.
- Tests run on the JVM, with no device and no emulator.

## 2. Non-goals (YAGNI, decided now)

Explicitly **out of the MVP** — each is additive later, none is a rewrite:

- **No SSE streaming.** A 300-500 char summary arrives in 3-8 s; a blocking request with a
  progress indicator is enough, keeps error handling simple, and avoids chunk-boundary, proxy
  buffering and `[DONE]` edge cases. The client interface stays one `suspend` function, so
  streaming is a new method later.
- **No category/feed digest ("AI aggregation").** Aggregating up to 100 articles is a different
  job with a different cost profile; the server-side pattern (digest published as a Miniflux feed)
  already exists and is not replaced by this.
- No target-language setting (it lives in the prompt), no `maxTokens` setting (constant), no
  multi-prompt library, no auto-summarize on open, no text-to-speech, no share-as-markdown.

## 3. Verified platform facts

Everything below was measured in the clone, not assumed. The design depends on these.

| Fact | Value |
| ---- | ----- |
| Persistence | **SQLDelight 2.3.2**, `deriveSchemaFromMigrations` + `verifyMigrations` enabled |
| Migrations | numbered `.sqm` in `capy/src/main/sqldelight/com/jocmp/capy/db/`, latest **26_AddArticleSyncStatuses.sqm** |
| Queries | per-table `.sq` files in the same directory (e.g. `articles.sq`, `saved_searches.sq`) |
| Timestamps | every date column is **`INTEGER` epoch seconds** (`published_at`, `updated_at`, `last_read_at`, `dismissed_at`); Kotlin side uses `nowUTC().toEpochSecond()` |
| Database scope | **per account** (`articles_$accountID`); one `.sqm` is applied to every account DB automatically |
| Foreign keys | `PRAGMA foreign_keys` is **never enabled** → `ON DELETE CASCADE` is inert; child cleanup is done by explicit statements in `articles.sq` |
| Article content | **not in the database**: the displayed text is `Article.content` held by the ViewModel, set to `""` while full-content extraction is loading; `Account.fetchFullContent()` (Account.kt:402) returns mercury-extracted HTML that is **never persisted** |
| Article deletion paths | `articles.sq`: `deleteAllArticles` (214), `deleteArticles` (237), `deletePageByID` (277), `deleteArticlesByID` (288); periodic orphan sweep at `Account.kt:233-236` (`deleteOldArticles` + `deleteOrphanedStatuses`) |
| HTML→text | Jsoup is `implementation` in `capy` → **not visible from `app`** |
| Module graph | `capy` already depends on `minifluxclient`, `readerclient`, `feedbinclient`, `rssparser`, `feedfinder` (house style: one module per backend) |
| DI | Koin, one module per feature (`ArticlesModule.kt`, `SettingsModule.kt`, `CommonModule.kt`); `koinInject()` is already used inside composables |
| UI injection precedent | `LocalFullContent` (`compositionLocalOf { FullContentFetcher() }`) already carries derived content into `ArticleView.kt:71` |
| HTTP | OkHttp 5.3.2; client modules use Retrofit + Moshi, `capy` uses kotlinx.serialization |
| Koin HTTP client | 30 s read timeout (`HttpClientBuilder.kt`) → too short for reasoning models |
| Flavors | **`gplay` is the default** → plain `assembleDebug` fails without `google-services.json`; always `assembleFreeDebug` |
| Signing | release/nightly run R8 and need `secrets.properties` + `release.keystore`; debug builds use a `.debug` applicationId suffix (coexists with the Play version) |
| Backups | `allowBackup="false"` **is already set** (`AndroidManifest.xml:21` + dataExtractionRules) → an adb backup cannot exfiltrate preferences; only a rooted device can |
| Toolchain | `compileSdk`/`targetSdk` 36, `minSdk` 30, JVM 21, AGP 8.11.1, Gradle wrapper 8.14.3, Kotlin 2.3.20 |
| Daemon JVM | pinned by the repo: `gradle/gradle-daemon-jvm.properties`, toolchain 21, **vendor JETBRAINS** → Gradle downloads its own JBR; `org.gradle.java.home` must **never** be set |
| Upstream build resources | `org.gradle.jvmargs=-Xmx2048m` (tight → override per invocation, never edit) |
| Tests | `capy/src/test/java/com/jocmp/capy/InMemoryDatabaseProvider.kt` gives a JVM in-memory DB |
| Migration check | `:capy:verifySqlDelightMigration` exists, but hangs off `check`, **not** `test` → CI (`make test`) will not catch a bad migration; run it locally |

## 4. Architecture

### 4.1 `aiclient` — new Gradle module (Kotlin/JVM library)

Follows the `minifluxclient` shape: a plain JVM library, no Android, testable without a device.
Registration is exactly two lines — `include(":aiclient")` in `settings.gradle.kts` and
`implementation(project(":aiclient"))` in `capy/build.gradle.kts`. Nothing else references it
(`app` reaches it transitively; there is no CI matrix, detekt or lint config to update).

```kotlin
// aiclient/build.gradle.kts
plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    kotlin("plugin.serialization") version libs.versions.kotlin // @Serializable DTOs
}
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.client)
    implementation(libs.jsoup)               // module-local: Jsoup is not exported by capy
    testImplementation(kotlin("test"))
    testImplementation(libs.tests.junit)
    testImplementation(libs.tests.kotlinx.coroutines)
    testImplementation(libs.mockwebserver3)  // OkHttp 5 MockWebServer (not the legacy v4, not gmazzo)
}
```

API surface:

```kotlin
interface SummaryClient {
    suspend fun summarize(request: SummaryRequest): Result<String>
}

data class SummaryRequest(val systemPrompt: String, val title: String, val text: String)

data class ProviderConfig(val baseURL: String, val model: String, val apiKey: String)
```

- `OpenAiCompatibleClient(config, httpClient)`: `POST {baseURL}/chat/completions` with
  `Authorization: Bearer`, body `{model, messages:[system, user], temperature:0.2, max_tokens:1024,
  stream:false}`, reads `choices[0].message.content` and **ignores `reasoning_content`**
  (emitted by deepseek-reasoner and GLM thinking models).
- `htmlToText(html: String): String` — Jsoup, **block-aware** (paragraph/list/heading boundaries
  become `\n\n`; `body().text()` alone flattens paragraphs and would degrade summary quality),
  removes script/style, decodes entities.
- Its own `OkHttpClient`: connect 15 s, read 120 s, call 120 s (Koin's client is 30 s). Calls run
  on `Dispatchers.IO`.
- No Retrofit: OkHttp and kotlinx.serialization are already in the tree, so one endpoint needs
  **zero new dependencies**. This is a deliberate deviation from the Retrofit+Moshi style of the
  other client modules, recorded so it is not "fixed" later by accident.

### 4.2 `capy` — cache and orchestration

- Schema: `27_CreateArticleSummaries.sqm` + `article_summaries.sq`.
- `SummaryRepository`: reads the config from `PreferenceStore`, checks the cache, calls
  `SummaryClient` on a miss, upserts the result. It takes the article text as an **input
  parameter** — it must never read article content from the database (see §3).
- `AiOptions` (preference accessor) next to the existing option classes.

Dependency direction: `app → capy → aiclient`. No module depends on `app`.

### 4.3 `app` — UI and wiring

- Card in `ArticleView.kt` (the `LocalFullContent` precedent), action in `ArticleBottomBar.kt`,
  one callback threaded through `ArticleScreen.kt`.
- **`ArticleScreenViewModel.kt` is not modified.** Summary state lives in its own small class
  injected with `koinInject()`. That file is ~900 lines and heavily refactored upstream; staying
  out of it is the single best merge-survival move.
- Settings: one new panel in `app/.../ui/settings/panels/SettingsPanel.kt` (`@Parcelize
  data object Ai : SettingsPanel(...)` + added to `companion items` as a **top-level** entry, since
  this is the fork's headline feature) and one panel file copied from `GeneralSettingsPanel.kt`,
  plus `strings.xml` and Koin registration.

## 5. Data flow

1. User taps **Summarize** in the article bottom bar.
2. Guards: no API key configured → the action is disabled with a hint pointing at settings;
   full-content extraction still `LOADING` → the action is disabled (content is empty at that
   moment); no network and no cache → explicit error.
3. The **currently displayed** `Article.content` is converted to text with `htmlToText`.
4. The text is truncated to a character budget (~24 000 chars) with a visible disclosure:
   "résumé établi sur les N premiers caractères".
5. Cache lookup on `(article_id, provider_key, prompt_hash)` where
   `provider_key = baseURL + "/" + model`. Hit → render, no HTTP.
6. Miss → `SummaryClient.summarize(...)`; on success the row is **upserted** and the card renders.
7. Manual **Re-summarize** bypasses the cache and overwrites the row (upsert, not insert).

## 6. Cache and schema

```sql
-- 27_CreateArticleSummaries.sqm
CREATE TABLE article_summaries (
  article_id   TEXT NOT NULL,
  provider_key TEXT NOT NULL,
  prompt_hash  TEXT NOT NULL,
  content      TEXT NOT NULL,
  created_at   INTEGER NOT NULL,
  PRIMARY KEY (article_id, provider_key, prompt_hash)
);
```

- Composite primary key matches the house idiom (`article_sync_statuses`, `saved_search_articles`).
- **`created_at` is `INTEGER` epoch seconds**, like every other timestamp in the schema — written
  with `nowUTC().toEpochSecond()`, not a formatted string.
- **No extra index**: `article_id` is the leading PK column, so the automatic PK index already
  serves both the lookup and cleanup-by-article. (Precedent for adding one exists only where the
  PK leads with another column: `saved_search_articles_article_id_index`.)
- **`provider_key` is part of the key** (not just the model name): switching provider with a reused
  model name must not silently reuse an unrelated summary.
- **`prompt_hash` is a runtime SHA-256 of the normalized prompt**, not a hand-bumped version
  constant: the prompt is editable at runtime, and a forgotten bump would serve stale summaries
  silently. No content hash — the summary input is the *displayed* content (extraction is never
  persisted), so hashing stored `content_html` would misvalidate permanently. Refetch drift is
  covered by the manual re-summarize and by displaying `created_at` on the card.
- Cleanup: add `DELETE FROM article_summaries WHERE article_id = :articleID` beside the existing
  child-table deletes in `articles.sq` — `deletePageByID` (277), `deleteArticlesByID` (288),
  `deleteAllArticles` (214), `deleteArticles` (237) — and a `deleteOrphanedSummaries` statement
  shaped like `deleteOrphanedStatuses` (267) called next to `Account.kt:235`. No new sweep
  infrastructure.
- The cache lives in the per-account DB, so deleting an account wipes it for free.
- **Merge rule**: the migration number is the one real collision point with upstream. On
  `git merge upstream/main`, if upstream also ships a `27_`, rename ours to the next free number
  (content is order-independent, our table references nothing upstream) and re-run the build.
  `verifyMigrations` fails loudly if this is missed — but only in `check`, so run
  `./gradlew :capy:verifySqlDelightMigration` explicitly.

## 7. Configuration and secrets

Follows the existing convention: inline snake_case keys grouped in a nested class in
`app/.../preferences/AppPreferences.kt` (like `ReaderOptions`, `ArticleListOptions`):

```kotlin
class AiOptions(private val preferenceStore: PreferenceStore) {
    val baseURL = preferenceStore.getString("ai_base_url", "https://api.deepseek.com/v1")
    val model   = preferenceStore.getString("ai_model", "deepseek-chat")
    val apiKey  = preferenceStore.getString("ai_api_key", "")
    val prompt  = preferenceStore.getString("ai_prompt", DEFAULT_SUMMARY_PROMPT)
}
```

| Key | Default |
| --- | ------- |
| `ai_base_url` | `https://api.deepseek.com/v1` |
| `ai_model` | `deepseek-chat` |
| `ai_api_key` | empty |
| `ai_prompt` | French summarization prompt (5 bullets max, no preamble, no follow-on text) |

**Verified provider endpoints** (live 401 auth probes, not guesses):

- DeepSeek: `https://api.deepseek.com/v1/chat/completions` (also answers without `/v1`)
- **Z.ai GLM (international): `https://api.z.ai/api/paas/v4/chat/completions`** — the documented
  GLM default; `https://open.bigmodel.cn/api/paas/v4/chat/completions` is the CN portal and also
  responds

The request shape (`{baseURL}/chat/completions`, Bearer, `stream:false`) stands; the **200
response body still needs one real call in M1** before the design is considered proven.

- **Accepted risk**: the key is stored in plain `SharedPreferences` (house style). `allowBackup` is
  already `false`, so adb backup cannot leak it; only a rooted device can. Single-user,
  non-distributed app, and the key is rotatable at the provider.
- The key is **never** committed, never defaulted, never logged. `secrets.properties` is for
  release signing and is unrelated.

## 8. UI

- **Card**: above the article, "Résumé · <model>" header, summary text, `created_at`, error state
  with retry, truncation disclosure when it applies.
- **States**: `idle → loading → done | error`. One state object, no global UI state.
- **Action**: an `IconButton` in `ArticleBottomBar.kt`'s `HorizontalFloatingToolbar` (+1 parameter
  to the composable), enabled only when a key exists and extraction is not loading.
- **Re-summarize**: from the card.
- With an empty key the button is simply disabled — the reading flow is unchanged.

## 9. Errors and edge cases

| Case | Behaviour |
| ---- | --------- |
| 401/403 | "clé refusée" + link to settings; no retry |
| 429 | explicit message, no automatic retry loop; manual retry |
| 5xx / network / timeout | error text + retry; cache untouched |
| Offline with a cached summary | cached summary renders |
| Offline without cache | explicit "hors ligne" error |
| Article content empty (extraction loading) | action disabled — never send an empty prompt |
| Very long article | truncation + disclosure line |
| Refetched live-blog entry | summary may describe an older revision; re-summarize is one tap |
| **Prompt injection** | article text is untrusted input: instructions inside it are never followed, the system prompt states the text is data, and only the configured endpoint is ever called |

## 10. Testing (JVM only, no device)

- `aiclient`: request shape (URL, `Authorization: Bearer`, model, temperature, `max_tokens`,
  `stream:false`), system/user message composition, response parsing (including a
  `reasoning_content` response being ignored), error mapping (401, 429, 5xx, timeout) — with
  **`libs.mockwebserver3`** (OkHttp 5 MockWebServer; not the legacy `com.squareup.okhttp3:mockwebserver`
  and not gmazzo's `libs.tests.okhttp.mock`, which is a different library the project also uses).
- `htmlToText`: tags/script/style removal, entity decoding, block-aware paragraph separation,
  empty input.
- `capy`: `SummaryRepository` cache round-trip on `InMemoryDatabaseProvider` — hit means **no HTTP
  call**, `provider_key` or `prompt_hash` change means a miss, upsert overwrites, article deletion
  removes rows.
- `./gradlew :capy:verifySqlDelightMigration` after any schema change.
- No instrumented (device) tests in the MVP; the real phone is the manual acceptance test.

## 11. Cost and limits

- On-demand + cache means one paid request per article, ever, per (provider, prompt) pair.
- `max_tokens` 1024, `temperature` 0.2, input trimmed to ~24 000 chars before sending.
- With the server-side digest path already in place for volume reading, there is no reason to
  auto-summarize; summarizing stays a user action.

## 12. Upstream maintenance

- `main` in the fork stays a **pristine mirror** of upstream (`git merge --ff-only upstream/main`).
  The spec and all code live on **`ai-summary`**; the spec travels with the code it describes.
- Sync with `git merge upstream/main` — **merge, never rebase** a long-lived published branch.
- Files touched in existing code: `ArticleView.kt`, `ArticleBottomBar.kt`, `ArticleScreen.kt`,
  `AppPreferences.kt`, `SettingsPanel.kt` + one new panel file, `strings.xml`, `articles.sq`,
  Koin registration, `settings.gradle.kts`, `capy/build.gradle.kts`, README note. Everything else
  is a new file.
- Never edit upstream-tracked `gradle.properties` or `.tool-versions`; a local JDK pin, if ever
  needed, goes in an untracked `mise.local.toml` (already in `.git/info/exclude`).
- GPL-3 §5(a): a public fork already satisfies the source obligation. The README gets one line:
  "Unofficial fork: adds AI article summaries (2026)". `LICENSE` and headers stay untouched. If an
  APK is ever handed to someone, the fork must stay public.

## 13. Build and run (measured)

```sh
# First build and every build after it (the repo pins its own daemon JDK; JBR 21 is auto-downloaded)
mise exec java@zulu-21.34.19.0 -- ./gradlew \
  -Dorg.gradle.jvmargs="-Xmx4g -Dfile.encoding=UTF-8" \
  -Dkotlin.daemon.jvmargs="-Xmx3g" \
  assembleFreeDebug          # never assembleDebug: gplay is the default flavor

# after any schema change (CI's `make test` does NOT run it)
./gradlew :capy:verifySqlDelightMigration

adb install -r app/build/outputs/apk/free/debug/app-free-debug.apk
```

Measured on this machine (Apple Silicon, 45 GiB free): SDK 360 MB, JDK 342 MB, cmdline-tools
173 MB, Gradle's JBR 1.0 GB, first `assembleFreeDebug` **3m 42s**. `local.properties` holds
`sdk.dir`; `$HOME/Library/Android/sdk/cmdline-tools` is a symlink to the Homebrew cask so AGP can
self-provision packages.

## 14. Milestones

1. **M1 — pipeline**: `aiclient` + request/response tests, action + card in the app, no cache.
   Proves the provider call (including one real 200 response) and the UI placement. Ends with one
   real summary on the phone.
2. **M2 — cache**: migration 27, `.sq`, `SummaryRepository`, cleanup hooks, cache-hit test.
3. **M3 — settings**: panel with base URL/model/key/prompt, disabled action without key,
   re-summarize.
4. **M4 — polish**: truncation disclosure, error UX, prompt injection note, README fork note.

## 15. Risks and open questions

- **Upstream refactors of `ArticleView.kt`/`ArticleBottomBar.kt`**: moderate; the edits are additive
  and small, which is why the state class stays out of `ArticleScreenViewModel.kt`.
- **GLM/DeepSeek 200 response shape** is unverified (only auth failures were probed); M1 must make
  one real call against the configured provider before the design is considered proven.
- **HTML→text quality** for full-content articles may lose code blocks and tables; accepted for the
  MVP, and the prompt asks for prose summaries.
- **Migration number collision** with upstream is certain eventually: mechanical, documented in §6.
