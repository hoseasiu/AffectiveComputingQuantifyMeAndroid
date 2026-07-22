# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

QuantifyMe is an Android app implementing an automated single-case experimental design (SCED)
self-experiment: users pick a question (e.g. "How does my leisure time affect my happiness?"),
the app walks them through a multi-stage self-experiment with daily check-ins, and reports which
target level produced the best outcome.

**This was originally a 2016-2018 client for a Django backend** (`AffectiveComputingQuantifyMeDjango`)
and a Jawbone UP wearable integration. Both are gone: the app has been fully modernized and is now
a **self-contained, offline-first Android app** — no server, no account, no network calls of any
kind. All experiment logic and data live on-device (Room + a ported Kotlin copy of the original
adaptive-experiment algorithm); wearable data comes from Health Connect instead of the defunct
Jawbone SDK.

**Read [`AGENT_PLANS/MODERNIZE.md`](AGENT_PLANS/MODERNIZE.md),
[`AGENT_PLANS/IMPROVEMENTS.md`](AGENT_PLANS/IMPROVEMENTS.md), and
[`AGENT_PLANS/COORDINATION.md`](AGENT_PLANS/COORDINATION.md) before making non-trivial changes.**
They are living documents (agents are expected to keep them updated) and are the actual source of
truth for *why* things are the way they are and *what's currently in progress* — this file is
deliberately just a map, not a duplicate. `MODERNIZE.md` has the full phase-by-phase modernization
history/rationale (backend removal, Jawbone→Health Connect, Compose migration, WorkManager swap).
`IMPROVEMENTS.md` has the current open backlog and a running "Landed"/"Still open" status table —
check its top-of-file status block before assuming something is or isn't done. **Before picking a
GitHub issue to work on, check `COORDINATION.md`** — it tracks which open issues depend on or
block each other, which files/modules multiple issues touch (merge-conflict risk even without a
hard dependency), and a snapshot of which issues currently have an active agent/worktree on them.
**Don't trust that snapshot's worktree list as complete or current in either direction** — a
worktree can be created or removed by any concurrent session (e.g. via `EnterWorktree`/
`ExitWorktree`) without the file being updated either way, so it can be missing brand-new worktrees
just as easily as it can be listing ones that are already gone. Run `git worktree list` yourself as
ground truth, and cross-check with `git -C <worktree-path> status --short` (per the file's own
notes) before assuming a worktree is idle or claimed — don't just filter the table. Update
`COORDINATION.md` when you start or finish an issue, whenever a dependency changes, **and whenever
you open a PR and again when it merges** — a merged PR closes an issue in fact even before GitHub's
own "Closes #N" auto-close lands, so the active-worktree table, the dependency graph, and the
file-overlap table can all go stale at that exact moment; don't wait for a later cleanup pass to
reflect it.

**Worktree setup — `AGENT_PLANS/` is gitignored, this file is not.** `CLAUDE.md` is tracked in git
specifically so it reaches every worktree automatically via ordinary checkout, regardless of how
the worktree was created (`EnterWorktree`, raw `git worktree add`, `claude -w`) — that's how you're
able to read this note at all in a fresh worktree. `AGENT_PLANS/` stays gitignored because
`COORDINATION.md` inside it churns every session and shouldn't pollute unrelated PR diffs, which
means **`git worktree add` never brings it into a new worktree** — check for it
(`Test-Path AGENT_PLANS/COORDINATION.md`) and self-heal before trusting its absence to mean
anything:
1. Find the main worktree: `git worktree list` — its first line, the one *not* under
   `.claude/worktrees/`.
2. Link, don't copy, so edits stay shared with every other worktree instead of silently rotting
   into a stale snapshot the moment they're made (this already happened once — a worktree copied
   only `MODERNIZE.md`, once, and never saw another update):
   ```powershell
   New-Item -ItemType Junction -Path AGENT_PLANS -Target "<main-worktree-path>\AGENT_PLANS"
   ```
3. If `AGENT_PLANS/` already exists here as a real directory rather than a junction to the main
   worktree, it's an old stale copy — don't overwrite it silently; reconcile it deliberately first.

Because `COORDINATION.md` is now the same physical file shared live across every linked worktree,
not a private copy: **always re-read it immediately before editing** (don't edit from a version
read several tool-calls ago — another session may have written to it since), and prefer appending
a dated `## Update: <date>` section or editing only your own row/section over rewriting whole
tables. This is already close to the file's existing style; the point is to make it deliberate now
that concurrent writers are real. This isn't a hard locking scheme — the file already treats its
active-worktree section as a lossy, re-derivable snapshot, so an occasional lost update is an
accepted cost, not a new risk.

## Build system

- Modern toolchain: Gradle 8.11.1, AGP 8.10.1, Kotlin 1.9.23, JDK 17, `compileSdk`/`targetSdk` 35,
  `minSdk` 26 (raised from 21 in the Health Connect migration — its client library requires API 26+).
  Full toolchain/version rationale and what got dropped/vendored/upgraded during modernization is in
  [`CONTRIBUTING.md`](CONTRIBUTING.md); read it before touching `build.gradle`.
- Standard commands (run from repo root; on Windows/Git Bash, use `gradlew.bat` directly if
  `./gradlew` fails to resolve `JAVA_HOME` — a known MSYS/`cygpath` quirk, not project-specific):
  - `./gradlew assembleDebug` / `gradlew.bat assembleDebug` — build debug APK
  - `./gradlew assembleRelease` — build release APK (falls back to your local
    `~/.android/debug.keystore` if no `keystore.properties` is present — installable but not
    store-ready)
  - `./gradlew testDebugUnitTest` — run the unit test suite
  - `./gradlew clean`
- **There is now a real unit test suite** (143 tests as of issue #21, PR #36) under `app/src/test/`
  — DAOs, `ExperimentEngine`/`ExperimentTypeConfig`, `ExperimentRepository` (including the three
  Health-Connect-backed experiment types), `HealthConnectManager` (via a `HealthConnectGateway`
  seam + `FakeHealthConnectGateway`, since the real client's response types have library-`internal`
  constructors a fake can't produce directly), `ExperimentExporter`, `CheckinReminderScheduler`/
  `AdherenceNudgeScheduler`, and ViewModels.
- **`app/src/androidTest` now exists** (issue #21): a `HiltTestRunner` (swaps in
  `HiltTestApplication`) and `ExperimentCheckinScreenTest`, a Compose UI test that seeds a real
  experiment into the on-device Room DB and drives the actual check-in wizard end-to-end via
  `ActivityScenario` + Compose test APIs. CI (`.github/workflows/android-ci.yml`) runs
  `testDebugUnitTest`/`assembleDebug`/`assembleRelease`/`assembleDebugAndroidTest` on push/PR to
  `master` — the last one only *compiles and packages* the instrumentation test APK. **No
  on-device/emulator verification happens automatically anywhere in this project** — the current
  `ubuntu-latest` CI runner has no emulator, so `ExperimentCheckinScreenTest` only ever runs when a
  human (or an agent with device/adb access) launches it manually. Wiring up an emulator in CI (e.g.
  `reactivecircus/android-emulator-runner`) is a known open follow-up, not done as part of #21. Most
  UI-facing changes land with an explicit "not visually verified on-device" note in `MODERNIZE.md`/
  `IMPROVEMENTS.md` — treat those notes as real gaps, not boilerplate.
- **Required local config before building** (all gitignored, none checked in):
  - `local.properties` — `sdk.dir=<path to your Android SDK>`.
  - `app/src/main/res/values/base_url.xml` — copy from `app/base_url.xml.template` (the template
    lives at the module root, not under `res/values/`, because AGP 8.x's resource merger rejects
    non-`.xml` files there). Defines `BASE_URL`/`ACRA_USER`/`ACRA_PASSWORD` string resources.
    **These are now vestigial** — the backend and ACRA network reporting are both gone, nothing
    reads these values at runtime — but the file must still exist for the resource merger to
    succeed. Any placeholder string values work fine.
  - `keystore.properties` — optional, only needed for a real release-signing keystore.

## Architecture

**Package root:** `edu.mit.media.mysnapshot`

### Experiment engine & data (fully local, Room-backed)

- `engine/ExperimentEngine.kt` is a faithful Kotlin port of the original Django backend's stage
  state-machine (`should_end_stage`, `is_output_stable`, `set_stage_targets`, `get_daily_target`,
  `get_valid_days`, `calculate_results`) — this is the actual research algorithm and must not be
  changed casually; changes here are product decisions, verified against
  `ExperimentEngineTest`/`ExperimentEngineCharacterizationTest` (the latter is an oracle ported
  directly from the Django source).
- `engine/ExperimentType` is **data-driven**, not a hardcoded closed set of subclasses: types are
  loaded from `assets/experiment_types.json` via `ExperimentTypeRegistry` (parsed once in
  `MyApplication.onCreate()`). A `SignalSource` enum is the fixed vocabulary of per-day signals
  (`CHECKIN_*`/`HEALTH_CONNECT_*`); a `FormatKind` enum drives instruction/target/result string
  formatting. Adding a new experiment type that reuses an existing signal/format is now a JSON +
  asset change; a genuinely new signal source still needs a new `SignalSource` + fetch
  implementation. Drawable/layout resource IDs stay in Kotlin maps in `ExperimentTypeRegistry`
  (compile-time R IDs can't live in JSON).
- `data/ExperimentRepository.kt` (Hilt-injected, coroutines/`Flow`-based) is the on-device
  replacement for every old REST endpoint (`start_experiment`, `experiment_checkin`,
  `refresh_instructions`, `cancel_experiment`) — orchestrates `ExperimentEngine` against Room.
- Room schema: `ExperimentEntity`, `CheckinEntity`, `UserProfileEntity` (unpopulated — no current
  consumer) + DAOs, mirroring the old Django models. Replaces both the old
  `SharedPreferences`-JSON-blob persistence and the backend database entirely.
- `health/HealthConnectManager.kt` wraps Health Connect reads (daily steps, nightly sleep duration,
  sleep efficiency, sleep start time) — the four signals the engine needs, replacing the dead
  Jawbone UP SDK. Requires Health Connect app installed + `READ_STEPS`/`READ_SLEEP` permissions.

### Activity flow

Same overall shape as before: `ExperimentChooseActivity` → `ExperimentIntroActivity` →
`ExperimentConfigActivity` → `ExperimentCreatedActivity` → daily `ExperimentCheckinActivity` /
`ExperimentInstructionsActivity` → `ExperimentCompleteActivity`, plus `HistoryActivity` and
`ExperimentProgressActivity` (opt-in mid-experiment visualization). All experiment activities are
`singleTask`, portrait-locked, and `@AndroidEntryPoint`-injected with `ExperimentRepository` —
none of them touch a backend or `SharedPreferences` for experiment state anymore.
`MainActivity.onCreate()` is the routing logic: no user data yet → `IntroActivity`; no/cancelled
experiment → `ExperimentChooseActivity`; finished → `ExperimentCompleteActivity`; otherwise → daily
check-in or instructions depending on whether today's check-in is done (`FORCE_CHECKIN = true` is a
deliberate preserved debug flag that always routes through check-in — see the comment at its
declaration before assuming it's a bug).

**Note for testing:** `MainActivity` skips `IntroActivity` entirely once onboarding is complete
(`SettingsActivity.hasSetUserData()`), so the fresh-install onboarding path is easy to leave
untested during normal iterative dev. Use `adb shell pm clear edu.mit.media.mysnapshot` to force a
clean run through it.

### UI layer — Compose/legacy-View hybrid, mid-migration

Migration is **partial**; check `IMPROVEMENTS.md` §3 for current status before assuming a screen is
Compose. As of the last update: `HistoryActivity`, `ExperimentChooseActivity`,
`ExperimentInstructionsActivity`, `ExperimentProgressActivity`, and the daily check-in
(`ExperimentCheckinActivity`) are Jetpack Compose (Material 3) with a `@HiltViewModel` +
`StateFlow<UiState>` + one-shot `Channel` event pattern (`viewmodel/` package — copy this
convention for new ViewModels). **Still the legacy Java `QuestionActivity` + `ViewPager` +
`ScrollPageIndicator` wizard framework**: `IntroActivity`, `SettingsActivity` (onboarding),
`ExperimentConfigActivity` (Kotlin, but still rides the Java framework), and the whole
`activities/questions/` + `view/` custom-widget zoo (`SelectableIcon*`, `ColoredRadioGroup`,
`TimePickerView`, `FontTextView`, etc.). `QuestionListener<T>` communicates answers back up
(`onSelected`/`onDataSave`/`onResetQuestion`); note `QuestionFragment.listener` is declared as the
**raw type** `QuestionListener` (no generic), so a mismatched `QuestionListener<T>` on a fragment
typed for a different `T` will compile silently and only fail at runtime — check this carefully
when wiring a new fragment/listener pair.

### Notifications

`notifications/CheckinReminderWorker`/`CheckinReminderScheduler` (daily check-in reminder) and
`AdherenceNudgeWorker`/`AdherenceNudgeScheduler` (mid-day encouragement nudge) are WorkManager-based
(`PeriodicWorkRequest` via `enqueueUniquePeriodicWork`), `@HiltWorker`-injected. Replaced the old
`AlarmManager`/`AlarmReceiver` (deleted) — no boot receiver needed since WorkManager persists its
own schedule across reboots. Both schedulers are called from `MyApplication.onCreate()` *and*
`SettingsActivity.onFinish()` (so a changed notification time takes effect immediately, not just on
next process start); `ExistingPeriodicWorkPolicy.UPDATE` makes the two calls idempotent together.
Requires `POST_NOTIFICATIONS` (Android 13+), requested from `SettingsActivity`.

## No networking layer

There is no backend, no Retrofit/OkHttp, no `INTERNET` permission, and no ACRA crash reporting —
all deleted during modernization (`BackendAPI`, `BackendAPIFactory`, both OkHttp interceptors, ACRA
were removed; see `MODERNIZE.md` Phase 2/3 and `IMPROVEMENTS.md` §1). The only file-system/sharing
I/O is a fully user-initiated, opt-in JSON export (`data/ExperimentExporter` + a share-sheet icon on
`HistoryActivity`, via `FileProvider`) — nothing automatic, nothing uploaded anywhere. Don't
reintroduce network calls without an explicit product decision; "no server, no account, no silent
network calls" is a deliberate, stated constraint of this rewrite, not an oversight to fix.

## On-device testing notes

- Physical-device screen timeouts are short by default and will interrupt an adb-driven test
  session; `adb shell svc power stayon usb` keeps the screen on while USB-connected.
- No backend means no `adb reverse` setup is needed to test the experiment flow — it's fully
  offline. (`base_url.xml`'s `BASE_URL` is unused, see Build system above.)
- `gh` is configured against `origin` (`hoseasiu/AffectiveComputingQuantifyMeAndroid`) for filing
  issues; GitHub Issues were disabled by default on this repo and were enabled during a 2026-07-21
  testing session — if they appear disabled again, that's a repo setting (`gh repo edit
  --enable-issues`), not a `gh` auth problem.
