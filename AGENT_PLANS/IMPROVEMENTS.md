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
| 3.2 | Onboarding wizard migrated to Compose; `activities/questions/` + `view/` zoo (except `FontTextView`, still used by `ExperimentComplete`/`CreatedActivity`) retired | #22 |
| 3.3 | Dark color scheme + optional Material You dynamic color | `adeaeee` |
| 4 | ACRA and LeakCanary 1.x dropped | `7961888` |
| 5 | Jawbone UP → Health Connect | PR #4 |
| 5.1 | Health Connect exercise-minutes signal | PR #37 (#29) |
| 6 | WorkManager replaces AlarmManager for reminders/nudges | PR #7 |
| 6.1 | Adherence support: target preview + mid-day nudges | PR #8 |
| 7.1 | Accessibility on the check-in screen only | `db2784d` |
| 7.5 | JSON-config experiment types + opt-in local export | PR #9 |
| 8 | Real unit-test suite (203 tests) + `androidTest` source set | PR #36 (#21) |
| 8 | Dead `UserProfileEntity`/`UserProfileDao` deleted | PR #30 (#24) |
| — | Fresh-install black screen; config Continue NPE; date-picker crash | PR #14, #16 |
| 7.4 | Hardcoded check-in wizard strings externalized to `strings.xml`; French/Spanish locales added | (#26) |
| 4 | Joda-Time → `java.time` swap (engine, Room, UI, notifications) | #23 (partial) |
| 2.2 | Finish ViewModel migration: `MainActivity`, `ExperimentComplete`, `ExperimentInstructions` | PR #40 (#19) |
| 7.2 | Portrait-only lock + `configChanges` overrides removed from all 13 activities; state-holding `remember`s promoted to `rememberSaveable`; legacy intro/created screens wrapped in `ScrollView` so long copy doesn't clip in landscape | (#25) |
| 7.5 | Two more built-in experiments recombining existing signals (`stepshappiness`, `leisureproductivity`), config-only | PR #38 (#28) |
| 9 | Custom-signal epic, all five parts: Room/engine data model (#31), data-driven check-in wizard (#32), "create your own experiment" wizard UI (#33), picker integration (#34), custom answers in JSON export (#35) | PR #45, #49, #50, #52, #51 |
| — | Removed MIT Media Lab/"research project"/consent-checkbox framing from onboarding's first screen (all 3 locales); plain informational screen, no accept gate | PR #55 (#54) |
| — | Fixed picker crash whenever a custom experiment type exists (flattened `experiment_choose_generic` to a single vector) | PR #59 (#56) |
| — | Deleted `PermissionCheckingAppCompatActivity`, fixing an infinite permission-request loop on Intro/Complete screens | PR #60 (#57) |
| 7.4 | Localized remaining hardcoded strings missed by #26: stage headers and "Today's Target" header on `ExperimentInstructionsActivity` | PR #61, #62 (#58) |

### Still open

| § | Item | Issue |
|---|---|---|
| 4 | gson bump, drop nineoldandroids/Picasso/roundedimageview/legacy-support (Joda swap landed) | *(orphaned — see note)* |
| 6.4 | Health Connect Play-readiness: rationale activity, privacy policy, empty states | #18 |
| 7.1 | Accessibility audit for every screen beyond check-in | #20 |
| 7.3 | Support multiple concurrent experiments | #27 |
| 10 | Emulator in CI so `androidTest` actually runs | *(no issue yet)* |
| 10 | `connectedDebugAndroidTest` hangs indefinitely even on a real physical device — `ExperimentCheckinScreenTest` (#21/#36) has never actually executed anywhere, only compiled | #63 |

> **Note on #23:** the issue was closed on 2026-07-22, but its own comments say the gson
> bump and nineoldandroids/Picasso/roundedimageview/legacy-support removal "remain open,
> gated on #22" — only the Joda→java.time swap was actually done under it. #22 landed the
> same day, so the gating condition is satisfied, but no new issue was filed for the rest and
> the dependencies are still in `app/build.gradle` today, confirmed unreferenced anywhere in
> `app/src/main/java`/`res`. This is real, doable work with no open issue tracking it —
> re-open #23 or file a fresh issue before assuming it's done.

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
`ExperimentInstructionsActivity`, `ExperimentProgressActivity`, `ExperimentCheckinActivity`,
and now (§3.2, #22) `IntroActivity`, `IntroThanksActivity`, `SettingsActivity`,
`ExperimentConfigActivity`.

**§3.2 landed:** the legacy `QuestionActivity`/`ViewPager` + the `activities/questions/`
fragment package + the 14-class `view/` zoo are gone, replaced by `SettingsViewModel`/
`ExperimentConfigViewModel` (the same `@HiltViewModel` + `StateFlow<UiState>` + one-shot
`Channel` convention as `CheckinViewModel`) and shared Compose wizard pieces in
`ui/wizard/WizardComponents.kt`. The onboarding/settings persistence model (`UserData`,
`NotificationData`, `loadUserData`/`saveUserData`) moved to `data/UserData.kt` since it's a
data concern, not a UI one — same Gson field names, so already-persisted on-device blobs
still round-trip. `FontTextView` is the one `view/` class kept, since `ExperimentComplete`/
`ExperimentCreatedActivity` (still legacy XML, #19's scope) still use it. The raw-`QuestionListener`
landmine CLAUDE.md warned about no longer exists — the new Compose callbacks are plain typed
lambdas. **Not visually verified on-device** (no emulator in CI, see §10) — only
`testDebugUnitTest`/`assembleDebug` were run.

## §4 — Dependencies

`joda-time` was the risky one: it was used throughout the **engine's date logic**, which is
the validated research algorithm. Swapped to `java.time` behind the full test suite (#23) --
`ExperimentEngine.kt` itself never referenced Joda directly, so the algorithm was untouched;
the change was mechanical everywhere else (`Converters`/entities → `OffsetDateTime`, engine/
repository/`HealthConnectManager` → `java.time.LocalDate`, the notification-time widgets →
`java.time.LocalTime`). Still open: bump `gson`, drop `nineoldandroids`/Picasso/
roundedimageview/legacy-support. #22 (the gating condition) landed the same day as the Joda
swap, so nothing blocks this anymore — but #23 was closed without a follow-up issue for it,
and the dependencies are still in `app/build.gradle`, confirmed unreferenced in source. Treat
this as open work with no tracking issue, not as done.

## §5 — Health Connect

Wraps steps, sleep duration, sleep efficiency, sleep start, exercise minutes. Tested via a
`HealthConnectGateway` seam + `FakeHealthConnectGateway`, because the real client's response
types have library-`internal` constructors. **§6.4** (#18) is what stands between this and a
Play listing.

## §7 — Product & polish

- **7.1 Accessibility** — only check-in is audited; everything else is unaudited (#20).
- **7.2 Rotation** — the 13 `configChanges` + `screenOrientation="portrait"` overrides that
  worked around the missing ViewModel layer are gone now that §2.2/#19 landed (#25). Screens
  already on ViewModels survive recreation for free; the handful of transient Compose
  `remember`s that held real user input (check-in/settings dropdowns, the history
  cancel-experiment reason dialog, the notification-time picker) were promoted to
  `rememberSaveable` so they don't reset on rotation either. Legacy XML screens
  (`ExperimentCreatedActivity`, the per-type `ExperimentIntroActivity` layouts) had their
  static copy wrapped in a `ScrollView` so long text can't get clipped behind the bottom
  button in landscape. **Not visually verified on-device/tablet** — no emulator in CI (§10);
  someone with a physical device or emulator should sanity-check rotation and a large-screen
  layout before considering this fully closed.
- **7.3 Concurrency** — single-active-experiment model is enforced today (#27).
- **7.4 Localization** — hardcoded Compose check-in wizard strings (and the ViewModel-built
  intro/sleep-explanation text) are now in `strings.xml`; French (`values-fr`) and Spanish
  (`values-es`) translations cover the full string table (#26). Adding further locales is now
  just a translation exercise — no more code changes needed unless new hardcoded copy is
  introduced elsewhere.
- **7.5 Experiment types** — data-driven via `assets/experiment_types.json` +
  `ExperimentTypeRegistry`. A type reusing existing signals is config + art only; a genuinely
  new signal needs a new `SignalSource` and a fetch implementation. Seven built-in types now
  (up from the original 4; #28 added `stepshappiness`/`leisureproductivity`, and an earlier
  pass added `exercisestress`). On top of that, §9 landed fully custom user-defined
  experiments, so the built-in list is no longer the ceiling on what someone can run.

## §8 — Testing

**Unit tests have a 10-minute task timeout** (`app/build.gradle`, `testOptions.unitTests.all`).
JUnit 4 has no global per-test timeout and Gradle waits forever by default, so one hung test
stalls the entire suite with the worker JVM at 0% CPU — which looks exactly like "the tests are
slow." That happened for real (`ExperimentCompleteViewModelTest`, fixed in #40). Per-test
`started` logging is on so the last STARTED line names the culprit when the timeout fires.

The suite runs in ~25s; a full cold build plus tests is under 2 minutes. If a run takes
materially longer, suspect a hang or Gradle lock contention (see §10), not genuine slowness.

203 JVM unit tests. `androidTest` exists but **no emulator runs anywhere in CI** (§10) —
`ExperimentCheckinScreenTest` only runs when launched by hand, and per #63, even a manual
launch on a real device currently hangs indefinitely rather than completing. UI changes
routinely land "not visually verified on-device"; those notes are real gaps, not boilerplate.

## §9 — Custom user-defined experiments (epic, #31–#35) — landed

All five parts merged 2026-07-22: `#31` (Room/engine data model — `CustomSignalDef`,
`SignalRef.Custom`, `CustomRangePresets`), `#32` (data-driven check-in wizard for custom
questions), `#33` (`CreateExperimentActivity`/`CreateExperimentViewModel`, the "create your own
experiment" wizard), `#34` (picker integration in `ExperimentChooseActivity`, including a
crash fix in #56 for when a custom type exists), and `#35` (custom-signal answers included in
`ExperimentExporter`'s JSON export). This touched the same Room schema as `#27`
(multiple-concurrent-experiments, still open) — see [`DEPENDENCIES.md`](DEPENDENCIES.md) for
that overlap if picking up #27 now.

## §10 — CI

`.github/workflows/android-ci.yml` runs `testDebugUnitTest`, `assembleDebug`,
`assembleRelease`, `assembleDebugAndroidTest` — the last only *compiles* the instrumentation
APK.

Real gaps:
1. **CI is `workflow_dispatch` only** (manual, since `ebf1e65`). Nothing runs on push or PR, so
   a branch is unverified unless someone runs the suite locally or triggers the workflow. Every
   PR review should confirm the suite was run.
2. **No emulator anywhere in CI.** Adding `reactivecircus/android-emulator-runner` would make
   `androidTest` actually execute. *(No issue yet.)*
3. **Even outside CI, `androidTest` has never actually run.** #63: `connectedDebugAndroidTest`
   hung indefinitely on a real physical device across three attempts (Gradle daemon alive,
   CPU flat, zero device-side process spawned, no logcat output) — a different failure mode
   than plain slowness, root cause not yet isolated. `ExperimentCheckinScreenTest` (#21/#36)
   has been compiled/packaged repeatedly but never actually executed anywhere.
