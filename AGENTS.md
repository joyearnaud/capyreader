# AGENTS.md

**Stop. Read the [README.md](./README.md) before doing anything.**

If you are an AI agent or a contributor using AI-assisted tooling, you must read and follow the contributing guidelines in the README before opening a pull request.

Key points:

- **Ask before submitting a PR.** Do not open unsolicited pull requests. See the [Pull Requests](./README.md#pull-requests) section.
- **Search existing discussions and issues first.** Do not create duplicate reports or requests.
- **Follow the project's code style and architecture.** Do not restructure, refactor, or "improve" code that wasn't requested.

PRs that ignore these guidelines will be closed.

---

## Fork notes (`joyearnaud/capyreader`)

This repository is a **personal fork** of `jocmp/capyreader`. Everything above still applies to
work intended for upstream. The fork also has its own maintenance policy.

**Read [docs/fork/maintenance-strategy.md](./docs/fork/maintenance-strategy.md) before any branch,
merge, rebase, or release operation.** It is the authority; this section is only a summary.

Non-negotiable:

- `main` mirrors `upstream/main`. **Fast-forward only (`git merge --ff-only`). Never commit on it.**
- `integration` is the fork's integration line — the build installed on the phone. It advances
  **only by merge**: never by direct commit, never by rebase.
- New work goes on a short-lived `feat/<topic>` branch off `integration`, merged in, then deleted.
- Upstream merges go through a throwaway `reconcile/<date>` branch, gated by
  `:app:testFreeDebugUnitTest :app:assembleFreeDebug` **and** `scripts/journeys/run.sh`, then
  fast-forwarded into `integration`. Never merge upstream directly onto `integration`.
- Build the **`free` flavor only** (`assembleFreeDebug`). Plain `assembleDebug` resolves to the
  default `gplay` flavor and fails without `google-services.json`.
- Tag every validated phone build: `git tag -a phone-YYYY-MM-DD`. `versionCode` is identical on
  every branch and cannot identify what is installed.
- `device-journeys/` is generated output — do not commit it.
- Do not open a PR upstream unprompted (see above). Upstream-bound work is cherry-picked onto a
  clean branch off `upstream/main`, never taken from `integration`.
