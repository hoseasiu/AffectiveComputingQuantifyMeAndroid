# QuantifyMe — Improvements backlog

> **Provenance note.** The original of this file was lost: `AGENT_PLANS/` was gitignored,
> so it had no history, and the directory was destroyed when a recursive delete followed a
> worktree junction into it. This version was **reconstructed on 2026-07-22** from the open
> GitHub issues (which cite this file's section numbers directly), merged PR history, and
> `git log`. The section numbering below is recovered from those citations, so existing
> references like "IMPROVEMENTS.md §7.2" still resolve. Wording is new.
>
> `AGENT_PLANS/` is now **tracked in git**, so this cannot recur.

**GitHub issues are the source of truth for open work.** This file gives the shape and
rationale; issues carry the current detail. Blocking order and merge-conflict risk live in
[`DEPENDENCIES.md`](DEPENDENCIES.md). Run `scripts/agent-status.ps1` to see who is working
on what — it is derived live, never stored here.

---

## Status

### Landed

| § | Item | Where |
|---|---|---|
| 1 | Backend, Retrofit/OkHttp, `INTERNET`, ACRA all removed | MODERNIZE Phase 2/3 |
| 1 | Privacy/manifest hardening; backup disabled; stale permissions pruned | `2a8ee57` |
| 2.1 | Room + `ExperimentEngine` replace SharedPreferences and the REST API | PR #1, #2 |
| 2.2 | ViewModel layer for History / ExperimentProgress / ExperimentChoose | `cf2c047` |
| 2.3 | Main-thread-blocking Room / Health Connect reads moved off the UI thread | `5cd4949` |
| 2.4 | `Converters` round-trips `DateTime` zone-faithfully | PR #10 |
| 3.1 | Daily check-in migrated to Compose + `CheckinViewModel` | `db2784d` |
| 3.3 | Dark color scheme + optional Material You dynamic color | `adeaeee` |
| 4 | ACRA and LeakCanary 1.x dropped | `7961888` |
| 5 | Jawbone UP → Health Connect | PR #4 |
| 5.1 | Health Connect exercise-minutes signal | PR #37 (#29) |
| 6 | WorkManager replaces AlarmManager for reminders/nudges | PR #7 |
| 6.1 | Adherence support: target preview + mid-day nudges | PR #8 |
| 7.1 | Accessibility on the check-in screen only | `db2784d` |
| 7.5 | JSON-config experiment types + opt-in local export | PR #9 |
| 8 | Real unit-test suite (147 tests) + `androidTest` source set | PR #36 (#21) |
| 8 | Dead `UserProfileEntity`/`UserProfileDao` deleted | PR #30 (#24) |
| — | Fresh-install black screen; config Continue NPE; date-picker crash | PR #14, #16 |

### Still open

| § | Item | Issue |
|---|---|---|
| 2.2 | Finish ViewModel migration: `MainActivity`, `ExperimentComplete`, `ExperimentInstructions` | #19 |
| 3.2 | Migrate onboarding wizard to Compose; retire `activities/questions/` + `view/` | #22 |
| 4 | Joda→`java.time`, gson, nineoldandroids/Picasso/roundedimageview/legacy-support | #23 |
| 6.4 | Health Connect Play-readiness: rationale activity, privacy policy, empty states | #18 |
| 7.1 | Accessibility audit for every screen beyond check-in | #20 |
| 7.2 | Remove portrait-only lock; rotation + tablet layouts | #25 |
| 7.3 | Support multiple concurrent experiments | #27 |
| 7.4 | Localize copy; externalize hardcoded Compose strings | #26 |
| 7.5 | More built-in experiments recombining existing signals | #28 (PR #38 open) |
| 9 | User-defined custom experiment signals (epic) | #31–#35 |
| 10 | Emulator in CI so `androidTest` actually runs | *(no issue yet)* |

---

## §1 — No networking

Deliberate and closed. No backend, no account, no silent network calls. The only I/O is the
user-initiated JSON export (`data/ExperimentExporter` + share sheet on `HistoryActivity`).
Do not reintroduce network calls without an explicit product decision.

## §2 — Data & architecture

`ExperimentRepository` (Hilt, coroutines/`Flow`) over Room is the on-device replacement for
every old REST endpoint. **§2.2 is the one still-open piece**: three screens still call the
repository directly with no ViewModel (#19). New ViewModels must follow the established
`@HiltViewModel` + `StateFlow<UiState>` + one-shot `Channel` convention in `viewmodel/`.

## §3 — UI migration (partial by design)

Compose (Material 3): `HistoryActivity`, `ExperimentChooseActivity`,
`ExperimentInstructionsActivity`, `ExperimentProgressActivity`, `ExperimentCheckinActivity`.

**§3.2 still legacy Java:** `IntroActivity`, `SettingsActivity`, `ExperimentConfigActivity`
ride `QuestionActivity` + `ViewPager` + the 14-class `view/` zoo (#22). Note the landmine
documented in CLAUDE.md: `QuestionFragment.listener` is a **raw** `QuestionListener`, so a
mismatched generic compiles silently and fails at runtime.

## §4 — Dependencies

`joda-time` is the risky one: it is used in the **engine's date logic**, which is the
validated research algorithm. Swap it behind the test suite, never casually (#23).

## §5 — Health Connect

Wraps steps, sleep duration, sleep efficiency, sleep start, exercise minutes. Tested via a
`HealthConnectGateway` seam + `FakeHealthConnectGateway`, because the real client's response
types have library-`internal` constructors. **§6.4** (#18) is what stands between this and a
Play listing.

## §7 — Product & polish

- **7.1 Accessibility** — only check-in is audited; everything else is unaudited (#20).
- **7.2 Rotation** — 13 `configChanges` + `screenOrientation="portrait"` overrides were
  workarounds for the missing ViewModel layer. **Depends on §2.2 / #19** (#25).
- **7.3 Concurrency** — single-active-experiment model is enforced today (#27).
- **7.4 Localization** — English-only; the Compose check-in migration added more hardcoded
  Kotlin strings. Blocked on target locales (product input) (#26).
- **7.5 Experiment types** — data-driven via `assets/experiment_types.json` +
  `ExperimentTypeRegistry`. A type reusing existing signals is config + art only; a genuinely
  new signal needs a new `SignalSource` and a fetch implementation.

## §8 — Testing

147 JVM unit tests. `androidTest` exists but **no emulator runs anywhere in CI** (§10) —
`ExperimentCheckinScreenTest` only runs when launched by hand. UI changes routinely land
"not visually verified on-device"; those notes are real gaps, not boilerplate.

## §9 — Custom user-defined experiments (epic, #31–#35)

`#31` (Room schema for custom signals) is foundational and blocks `#32`–`#35`. It touches
the same Room schema as `#27`; see [`DEPENDENCIES.md`](DEPENDENCIES.md) before starting
either.

## §10 — CI

`.github/workflows/android-ci.yml` runs `testDebugUnitTest`, `assembleDebug`,
`assembleRelease`, `assembleDebugAndroidTest` — the last only *compiles* the instrumentation
APK.

Two real gaps, neither with an issue yet:
1. **CI is `workflow_dispatch` only** (manual, since `ebf1e65`). Nothing runs on push or PR, so
   a branch is unverified unless someone runs the suite locally or triggers the workflow. Every
   PR review should confirm the suite was run.
2. **No emulator anywhere.** Adding `reactivecircus/android-emulator-runner` would make
   `androidTest` actually execute.
