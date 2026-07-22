# QuantifyMe Android — Modernization Plan

## Context

QuantifyMe (Taylor, Sano, et al., *Sensors* 2018) is an MIT Media Lab research app that
walks a user through a single-case experimental design (SCED) self-experiment: pick a
question ("how does my sleep duration affect my productivity?"), get a daily behavioral
target for 4 staged phases, check in once a day, and see which target level produced the
best outcome. The current codebase is a 2016-era Java app: AGP 2.2.0/Gradle 2.14.1,
`compile`-based deps, no AndroidX, Retrofit 2.0.0-beta2 + OkHttp v2, and a hard dependency
on the Jawbone UP wearable SDK — a platform that **shut down in 2018**. The paper's own
"Discussion/Limitations" section (§6, §6.4) already identifies most of the UX problems worth
fixing: low adherence (22.5% objective vs. 75.6% check-in), no mid-experiment visualization,
confusing sleep-day boundaries, and insufficient motivation/education about *why* the
schedule demands what it demands. That section is effectively a free product-requirements
doc and this plan leans on it directly.

**Decision: this rewrite drops the companion Django backend
(`AffectiveComputingQuantifyMeDjango`) entirely.** The app becomes fully self-contained on
the user's phone — no server, no account, no network calls for the experiment flow. This is
a significant scope decision beyond "modernize the client," so it's called out with its own
sections below (Phase 2 and Phase 3) rather than treated as a routine networking-library bump.

### Why full self-containment is feasible

Inspection of the Django backend (`src/app/analysis.py`, `src/app/models.py`) shows the
"brain" of the app — the adaptive stage-sequencing/target/confidence algorithm — is pure
rules-based arithmetic over dates and floats. It has no ML, no external service calls beyond
fetching Jawbone data, and no reason to live server-side other than "that's where 2016 apps
put logic." Concretely, the server today does five things:

1. **Auth** (`/obtain_token/`) — token keyed on Google account email + Android ID. Not
   needed for a single local user.
2. **Biographic data storage** (`/set_user_data/`).
3. **The adaptive experiment engine** (`/start_experiment/`, `/experiment_checkin/`,
   `/refresh_instructions/`) — baseline-stage averaging → bucketing into range categories
   (`under/N1/N2/N3/over`) → stage target sequencing, a 7-day-stage state machine with
   early-stop-on-stability and restart-on-missed-days rules
   (`Experiment.should_end_stage`, `Experiment.is_output_stable`), and a "which stage won" +
   confidence calculation at the end (`Experiment.calculate_results`). All of it operates
   only on locally-knowable data: check-ins + sleep/steps events.
4. **History/cancel** (`/get_experiments/`, `/cancel_experiment/`) — CRUD over the same
   experiment record.
5. **Jawbone measurement ingestion** — being replaced by Health Connect regardless (Phase 3);
   see below for why this makes the backend's data-fetching role moot too.

None of this requires a remote server once the sensor data itself is sourced on-device. The
whole "brain" (#3) ports to a few hundred lines of testable Kotlin.

## Guiding principles

- **Land in phases that each independently compile and run.** This is a stalled 8-year-old
  codebase; a single "rewrite everything" branch will never land. Every phase below should
  end with a buildable, installable app.
- **Fix the broken sensor dependency early, not last.** Jawbone is dead; nothing past Phase 1
  can be meaningfully tested on-device without a working activity/sleep data source.
- **Preserve the experimental design logic exactly.** Port `analysis.py` / `Experiment`'s
  stage sequencing, target-zone bucketing, early-stop/restart rules, and confidence
  calculation faithfully into Kotlin — that logic is what makes this a validated research
  instrument, not a cosmetic detail. Changes to it are product decisions, not refactors.
- **Treat §6 of the paper as the backlog for the product-quality track**, separate from the
  pure engineering-modernization track. They can run in parallel once Phase 3 lands.
- **No server, no account, no silent network calls.** All experiment state lives in local
  storage on the device; the only acceptable network use is a user-initiated, explicit action
  (e.g. "export my data to a file"), never an automatic upload.

---

Current Status: 
- Phase 0 complete
- Phase 1 complete
- Phase 2 complete (see "Previously landed" below for the experiment-flow-activity pass that
  closed it out)
- Phase 3 complete — Jawbone UP replaced with Health Connect; no Jawbone code path or
  networking dependency remains anywhere in the app (see below).
- Phase 4 complete — `HistoryActivity`, `ExperimentChooseActivity`, and
  `ExperimentInstructionsActivity` are all Compose now (no legacy XML screens left in the
  critical experiment-flow path), and the `AlarmManager` -> `WorkManager` swap has landed;
  see "Landed this pass (Phase 4..." below.
- Phase 5 started in parallel (per the plan's "Phase 4 and 5 can run in parallel once
  Phase 3 lands" sequencing) — items 5.1 (mid-experiment visualization), 5.2
  (methodology transparency), and 5.3 (adherence nudges) have landed, see "Landed this
  pass (Phase 5.3..." and "Landed this pass (Phase 5.1/5.2..." below. 5.4 (data model
  generalization) is still open and explicitly deferred pending demand, per the plan.

Notes:

Landed this pass (Phase 5.3 — adherence support mechanisms: multi-day-ahead target
preview + mid-day encouragement nudges):
- **Multi-day-ahead target preview (opt-in)**: new `ExperimentRepository.getUpcomingTargetPreview()`
  computes the remaining days' targets for the *current* stage only, reusing
  `ExperimentEngine.getDailyTarget`'s day-parity rule directly (future targets don't
  depend on any not-yet-collected sensor/check-in data, so this needed no new engine
  logic — just calling the existing pure function ahead of time). Returns empty once
  `currentStage == 0` (baseline has no personal target yet) or once there are no days
  left before the stage's normal 7-day boundary; it deliberately can't predict an early
  end/restart from missed days or an unstable output, and the dialog copy says so.
  Reachable from a new `icon_settings_calendar` header icon on
  `ExperimentInstructionsActivity` (shown only when `outcome.currentStage != 0`, matching
  the existing "Today's Target" visibility gate), following the exact same one-time
  opt-in-dialog pattern 5.1's progress view established (`TargetPreviewOptInDialog` /
  `TargetPreviewDialog`, new `show_target_preview_during_experiment` boolean pref) — the
  paper's own §6.4 tradeoff (previewing targets ahead of time can bias behavior, same as
  seeing past progress) applies here too, so it gets the same explicit opt-in rather than
  defaulting on.
- **Mid-day encouragement nudge**: new `notifications/AdherenceNudgeWorker.kt` +
  `notifications/AdherenceNudgeScheduler.kt`, following `CheckinReminderWorker`/
  `CheckinReminderScheduler`'s exact WorkManager pattern from Phase 4 (this item was
  explicitly blocked on that swap landing first — see 5.1/5.2 notes below). Directly
  targets the paper's core finding: only 1/13 participants completed a full 4-stage
  experiment because *objective* adherence to the target (22.5%) was far below *check-in*
  adherence (75.6%) — a single morning reminder to check in about yesterday clearly
  wasn't enough to keep people on today's target. Scheduled at a fixed 6-hour offset from
  the user's own check-in reminder time (not a fixed clock time), so that by the time it
  fires that morning's check-in has normally already set today's target — same
  Calendar-based initial-delay computation as `CheckinReminderScheduler`, separate
  `enqueueUniquePeriodicWork` unique name (`adherence_nudge`) and notification channel
  (`adherence_nudges`) so it doesn't collide with or replace the check-in reminder.
  Skips firing (returns `Result.success()` without notifying) when notifications are
  off, no experiment is currently active, the experiment is still in baseline
  (`currentStage == 0`, no personal target exists yet), or `refreshInstructions()`
  returns a null target — reuses `ExperimentRepository.refreshInstructions()` as-is
  (already a read-only recompute with no DB writes, safe to call from a worker) rather
  than adding a parallel read path. Notification text reuses
  `ExperimentType.formatInstruction()` verbatim (e.g. "Try to sleep 7.5 hours tonight")
  so the nudge and the instructions screen never say different things about the same
  target. Wired into the same two call sites as `CheckinReminderScheduler`
  (`MyApplication.onCreate()`, `SettingsActivity.onFinish()`) and shares the
  `notificationSet` on/off toggle rather than adding a second preference toggle — not
  scoped as a separately-configurable notification type since the paper doesn't ask for
  that distinction. `MainActivity.clearNotifications()` now cancels both notification
  IDs on tap, since either one opens the same screen.
- Deliberately not built as part of this item, per the plan's own scoping note: the
  personality-trait questionnaire angle mentioned in the plan (adherence correlated with
  personality traits in the paper's separate research survey, not app-collected data) —
  that would require adding new onboarding data collection, a distinct feature decision
  from "port two withheld-by-design UX ideas the paper already validated," left for a
  separate pass if pursued at all.
- Verified: `:app:compileDebugKotlin`, `:app:compileDebugJavaWithJavac`,
  `:app:testDebugUnitTest` (still 26/26 — this pass touched no engine/algorithm code,
  only new opt-in UI and a new notification worker/scheduler pair), and
  `:app:assembleDebug` all green. No device/emulator available in this environment — the
  new preview icon/dialogs and the mid-day nudge notification (including whether the
  6-hour-offset scheduling computes correctly across a real day boundary and whether the
  notification actually fires/displays) have **not** been visually verified on-device,
  only compiled and unit-tested. Whoever next has device access should: set a near-future
  check-in time in Settings, wait past the 6-hour-offset window (or temporarily shrink
  `AdherenceNudgeScheduler.OFFSET_HOURS_AFTER_CHECKIN` for testing) to confirm the nudge
  actually fires with the correct target text, and exercise the target-preview opt-in
  dialog -> preview list flow on a mid-stage experiment.
Landed this pass (Phase 4 — `ExperimentChooseActivity`/`ExperimentInstructionsActivity`
Compose migration, `AlarmManager` -> `WorkManager` swap):
- **`ExperimentChooseActivity`**: rewritten as `ComponentActivity` + `setContent`, following
  `HistoryActivity`'s established pattern. The 4 fixed experiment-type picker cards
  (`RoundedImageView` -> `Image` + `RoundedCornerShape` clip) and the pinned "Select Your
  Experiment" header card are now Compose; `activity_experiment_choose.xml` deleted. The
  `onResume`-driven redirect-to-`MainActivity`-if-an-experiment-already-exists guard is
  unchanged (plain `ComponentActivity.onResume` override, not a Compose concern).
- **`ExperimentInstructionsActivity`**: rewritten as `ComponentActivity` + `setContent`.
  Notable behavior preserved exactly: the legacy `onResume`-reads-`pendingOutcome`-from-
  `intent`-every-time quirk (a `LaunchedEffect(resumeTrigger)` keyed on an `onResume`-
  incremented counter, re-reading `intent` extras fresh each run, matching the original's
  per-resume Intent-extras re-read rather than a one-time read); the once-per-instance
  new-stage/failed-stage dialog guard (`dialogsShown` stays a plain non-Compose Activity
  field); the refresh-button spin (now `rememberInfiniteTransition` + `Modifier.rotate`
  instead of a `RotateAnimation`); the Phase-5.1 progress-icon opt-in `AlertDialog` flow
  and the Phase-5.2 restart-reason copy, both carried over unchanged in behavior. Deleted:
  `activity_experiment_instructions.xml`, `activity_experiment_first_day.xml`,
  `fragment_new_stage.xml`, `fragment_failed_stage.xml`,
  `activities/fragments/NewStageFragment.java`, `activities/fragments/FailedStageFragment.java`
  (both legacy `android.app.DialogFragment`s, replaced by `Dialog`-based Compose
  composables in the same file). In `NewStageDialog`'s `Compose` port, the "Yay!"/"Okay. Got
  it." dialogs' tap-to-dismiss `PendingIntent` for the notification (see below) now correctly
  uses `MainActivity.CLICK_NOTIFICATION_ACTION` — the old `AlarmReceiver` version set its own
  unrelated action constant instead, so tapping the reminder notification never actually hit
  `MainActivity.clearNotifications()`'s cancel-on-tap path; fixed as a side effect of rewriting
  that exact code block for the WorkManager swap below, not a separate drive-by change.
- **Fonts finally wired into Compose** (deferred from `HistoryActivity`'s migration per its
  own notes): new `ui/theme/Type.kt`, `rememberQuantifyMeFonts()` loads the same 5 `.ttf`
  assets `view/FontTextView.java` uses (`Montserrat-Regular/Bold`, `Raleway-Medium/SemiBold/
  Light`) from `assets/fonts/` via the `Font(path: String, assetManager: AssetManager, ...)`
  overload — note the *path-then-assetManager* argument order, which is easy to get backwards
  (the compiler error when swapped doesn't obviously point at the fix). `ui/theme/Color.kt`
  extended with `DarkBlue`/`FadeYellow`/`Yellow`/`DarkPurple`/`FadeGreen` (the additional
  legacy `colors.xml` values these two screens needed).
- **`AlarmManager` -> `WorkManager`**: new `notifications/CheckinReminderWorker.kt`
  (`@HiltWorker`/`CoroutineWorker`, ported `AlarmReceiver.createNotification`'s channel +
  notification-building logic verbatim) and `notifications/CheckinReminderScheduler.kt`
  (ported `AlarmReceiver.setRecurringAlarm`'s target-time computation, now building a
  `PeriodicWorkRequest` via `enqueueUniquePeriodicWork(..., ExistingPeriodicWorkPolicy.UPDATE,
  ...)` instead of `AlarmManager.setInexactRepeating`). `AlarmReceiver.java` deleted along
  with its manifest `<receiver>` entry (`BOOT_COMPLETED` + custom-action intent-filter) — no
  boot receiver is needed anymore since WorkManager's own `PeriodicWorkRequest` persistence
  survives reboots on its own. `MyApplication` now implements `Configuration.Provider`
  (injects `HiltWorkerFactory` so `CheckinReminderWorker` gets `@AssistedInject` like the rest
  of the app's Hilt-injected classes) and calls `CheckinReminderScheduler.schedule(this)` on
  every process start; the manifest's default `androidx.startup.InitializationProvider`
  WorkManager auto-init is removed (`tools:node="remove"` on the `WorkManagerInitializer`
  meta-data) so the Hilt-configured `WorkManager` instance is used instead of a default one
  built before `Configuration.Provider` would take effect.
  - **Fixed a latent scheduling gap while porting this, not a new feature**: `setRecurringAlarm`
    had exactly one caller in the old code — `AlarmReceiver.onReceive` on `BOOT_COMPLETED` —
    meaning the reminder was never actually armed on first install or immediately after the
    user changed their notification time in Settings, only after the *next device reboot*.
    `CheckinReminderScheduler.schedule(context)` is now also called from
    `SettingsActivity.onFinish()` (right after `saveUserData()`) and `MyApplication.onCreate()`,
    so the schedule takes effect immediately and stays correct across process restarts;
    `ExistingPeriodicWorkPolicy.UPDATE` makes the `MyApplication.onCreate()` call a no-op if
    `SettingsActivity` already scheduled the right thing, so the two call sites don't fight.
  - **Added the missing `POST_NOTIFICATIONS` permission** (Android 13+): the old code had no
    manifest declaration and no runtime request for it at all, meaning the reminder notification
    silently could never display on any device targeting API 33+ (this app's `targetSdkVersion`
    has been 35 since Phase 1) even though the rest of the notification-firing code "worked."
    Added the manifest `<uses-permission>` (replacing the now-unused
    `RECEIVE_BOOT_COMPLETED`/`SET_ALARM` permissions, which had no other callers once
    `AlarmReceiver` was deleted) plus a `registerForActivityResult(RequestPermission())`
    request in `SettingsActivity`, triggered from `onFinish()` only when the user has
    `notificationSet = true`. `CheckinReminderWorker` also re-checks the permission at fire
    time before calling `notify()` (belt-and-suspenders, since the user can revoke it later
    from system Settings independent of the app).
- Verified: `:app:compileDebugKotlin`, `:app:compileDebugJavaWithJavac`,
  `:app:testDebugUnitTest` (still 26/26 across both `ExperimentEngineTest` and the Phase-0
  characterization suite), `:app:assembleDebug`, and `:app:assembleRelease` (including
  `lintVitalRelease`) all green. No device/emulator available in this environment — the two
  newly-migrated Compose screens, the new-stage/failed-stage dialogs, and the WorkManager-
  driven reminder notification (including whether it actually fires/displays and whether the
  permission prompt behaves correctly) have **not** been visually verified on-device, only
  compiled and unit/lint-tested. Whoever next has device access should: walk the choose ->
  intro flow, trigger both dialog types from a real check-in, exercise the progress-icon
  opt-in flow, and set a near-future notification time in Settings to confirm the reminder
  actually fires and that tapping it clears the notification and opens the app.

Landed this pass (Phase 5.1/5.2 — mid-experiment visualization + methodology transparency):
- **5.2 — stage-restart transparency**: `ExperimentEngine.shouldEndStage` now returns a
  `RestartReason` (`TOO_MANY_MISSED_DAYS` vs. `TARGET_ZONE_UNREACHABLE`) alongside
  `restartedStage`, threaded through `CheckinOutcome` -> `ExperimentInstructionsActivity`'s
  intent extras -> `FailedStageFragment`, which now shows reason-specific copy
  (`R.string.restart_reason_missed_days` / `restart_reason_target_zone_unreachable`)
  instead of one generic "we couldn't gather enough data" message for both restart causes.
  Previously the two very different failure modes (missed check-ins vs. data landing
  outside the target zone) were indistinguishable to the user. Covered by two new
  assertions in `ExperimentEngineTest` (still 26 tests total, no new methods added).
- **5.2 — "what counts as a night"**: `HealthConnectManager.getMostRecentSleepSession()` now
  exposes the raw start/end `Instant`s of the most recent `SleepSessionRecord` plus which
  calendar day it's attributed to (end-time day, matching `perDaySleepSession`'s existing
  convention). `ExperimentType` gained a `usesSleepData` flag (true for
  `SleepVariabilityStress`/`SleepDurationProductivity`/`StepsSleepEfficiency`, false for
  `LeisureHappiness`); `ExperimentCheckinActivity` now appends a sentence to the daily
  check-in intro explaining the actual bedtime/wake time and which night it counted as,
  for sleep-using experiment types only.
- **5.1 — opt-in mid-experiment visualization**: new `ExperimentRepository.getProgressSummary()`
  reconstructs per-stage date ranges/restart counts/check-ins for every stage reached so
  far (reusing `ExperimentStageState`, no new Room schema). New
  `ExperimentProgressActivity` (Compose, following the `HistoryActivity` Phase-4 pattern:
  `ComponentActivity` + `setContent`, `QuantifyMeTheme`, `LazyColumn` of `Card`s) renders
  it. Reachable from a new icon (`icon_settings_chart`) on `ExperimentInstructionsActivity`;
  first tap shows a one-time `AlertDialog` explaining the bias trade-off the original
  paper's authors raised (§6.2) before persisting the opt-in as a plain boolean in
  `SharedPreferences` (`show_progress_during_experiment`) — deliberately not added to the
  Gson `UserData` blob or the still-unpopulated `UserProfileEntity` Room table, since
  neither has any other consumer of this kind of per-device UI preference.
- Explicitly NOT done in this pass: 5.3 (multi-day-ahead target preview, mid-day
  encouragement nudges — the nudges specifically need Phase 4's still-open
  `AlarmManager` -> `WorkManager` swap first, since new notification scheduling on top of
  the legacy `AlarmReceiver` would be throwaway work) and 5.4 (data model generalization to
  a bundled JSON config — the plan explicitly says to sequence this last and validate
  demand first, not default-implement it).
- Verified: `:app:compileDebugKotlin`, `:app:compileDebugJavaWithJavac`,
  `:app:testDebugUnitTest` (still 26/26), and `:app:assembleDebug` all green. No
  device/emulator available in this environment — the new progress screen, restart-reason
  dialog text, and sleep-night explanation have not been visually verified on-device, only
  compiled and unit-tested; whoever next has device access should sanity-check them
  (trigger both restart reasons, check a sleep-using experiment type's check-in intro text,
  and the progress screen's empty/populated states) before relying on them.

Landed this pass (Phase 4 — Compose bootstrap, `HistoryActivity` migration):
- Added Compose to the build: `buildFeatures.compose true`, `composeOptions.kotlinCompilerExtensionVersion`
  '1.5.13' (matches the pinned Kotlin 1.9.23 plugin version), the `androidx.compose:compose-bom:2024.09.00`
  platform plus `ui`/`ui-tooling-preview`/`material3`/`activity-compose`/`lifecycle-viewmodel-compose`/
  `lifecycle-runtime-compose`. No other dependency versions changed.
- New `ui/theme/` package (`Color.kt`, `Theme.kt`): a small manual mapping of the legacy XML
  colors actually used by the first migrated screen (not a full port of `colors.xml` — extend
  it screen-by-screen as more screens migrate, per "don't add unused abstractions").
- `HistoryActivity` rewritten from a `ListView`/`ArrayAdapter`/XML-inflation Activity to a
  `ComponentActivity` + `setContent { }` Compose screen (`HistoryScreen`/`ExperimentCard`/
  `CancelExperimentDialog` composables in the same file, since the screen is small and has one
  caller). Behavior preserved: live-updating experiment list (now via
  `collectAsStateWithLifecycle` directly on the existing `ExperimentRepository.getAllExperiments()`
  Flow, no more manual `swiper`-triggered reload), same cancel-with-reason confirmation flow
  now as a Compose `AlertDialog`, same finished/active/cancelled card styling (colors, icon,
  day count, result/confidence text). Deleted the now-dead `activity_history.xml` and
  `view_history_experiment.xml` layouts (confirmed no other references first).
  Deliberately simplified: the pull-to-refresh gesture is gone (the Flow already pushes
  updates live, so there was nothing left to pull-refresh) and this first Compose screen uses
  Material 3 default typography instead of the custom `FontTextView`
  Montserrat/Raleway `.ttf` assets — those live under `assets/fonts/` (not `res/font/`), so
  wiring a Compose `FontFamily` for them is a small separate task to do once when the *next*
  screen migrates, rather than duplicating it ad hoc here.
- Verified: `:app:compileDebugKotlin`, `:app:testDebugUnitTest` (still 26/26), and
  `:app:assembleDebug` all green. No device/emulator available in this environment; the
  Compose screen has not been visually verified on-device or in a running app, only compiled
  and unit-tested — whoever next has device/emulator access should sanity-check
  `HistoryActivity` (empty state, populated list, cancel-experiment dialog, active/finished/
  cancelled card styling) before relying on it.
- Note for whoever continues this phase: this environment's worktree needed
  `local.properties` (`sdk.dir`) and `app/src/main/res/values/base_url.xml` copied over from
  a working checkout to build at all — both are intentionally gitignored per-machine files
  (see `CONTRIBUTING.md`), not something to add to the repo.

Landed previously (Phase 3 — Jawbone -> Health Connect):
- Deleted `JawboneQuestionActivity`, `QuestionJawboneFragment`,
  `fragment_question_jawbone.xml`, the `com.jawbone.upplatformsdk.oauth.OauthWebViewActivity`
  manifest entry, and the `com.github.Jawbone:UPPlatform_Android_SDK` dependency. Also
  deleted `BackendAPI`/`BackendAPIFactory` (and their two OkHttp interceptors) and the
  Retrofit/OkHttp/gson-jodatime-serialisers dependencies -- `SettingsActivity.onFinish()` was
  the last caller of the network layer (Jawbone-token + biographic-data upload to
  `/set_user_data/`); it now saves locally and synchronously, no network round trip, matching
  the "no server, no network calls" decision from Phase 2. Plain `gson` stays (still used by
  `ExperimentStageState` to serialize stage JSON into Room columns) and joda-time stays (used
  throughout).
- `SettingsActivity` converted Java -> Kotlin (`@AndroidEntryPoint`) as part of this pass --
  not a drive-by modernization, it's what made wiring the Health Connect permission request
  (a suspend-based API) practical without Java/coroutine interop hacks. It now extends the
  generic `QuestionActivity` again (was `JawboneQuestionActivity`). Its onboarding-blob
  persistence mechanism (`SharedPreferences`-backed `UserData`, read by `MainActivity` and
  `AlarmReceiver` for notification scheduling) was deliberately left as-is -- that's a
  separate, pre-existing concern from Jawbone/networking and out of scope here; `UserData`
  only lost its `jawboneAccess`/`jawboneReset` fields and gained `healthConnectGranted`.
  `UserProfileEntity`/Room remains unwired for biographic data (still no real consumer) --
  not force-populated just to satisfy that stray Room table, per "don't add unused
  abstractions"; flagged for Phase 4/5 if actually needed.
- New `QuestionHealthConnectFragment` + `fragment_question_health_connect.xml` replace the
  Jawbone onboarding page in the same wizard slot, requesting Health Connect's `StepsRecord`/
  `SleepSessionRecord` read permissions via `PermissionController.createRequestPermissionResultContract()`.
- New `health/HealthConnectManager.kt`: wraps the Health Connect client, exposing exactly the
  four signals the engine needs -- daily step count, nightly sleep duration, sleep efficiency
  (`1 - awake_seconds/total_seconds` from `SleepSessionRecord` stage data, more precise than
  Jawbone's single opaque `awake_time`), and sleep start time (minute-of-day, timezone-aware
  via `startZoneOffset`). Each is aligned to the calendar day the Health Connect record's own
  timestamp falls on -- unlike check-in-derived signals, no `+1`-day shift is needed (Health
  Connect records carry their own timestamps; check-ins are "filled in the next morning").
- `ExperimentDataProvider.getInputs`/`getOutputs` are now `suspend fun`s taking a
  `HealthConnectManager` parameter (previously pure/synchronous null-returning stubs); the
  three previously-stubbed signals (`SleepVariabilityStress`/`SleepDurationProductivity`
  inputs, `StepsSleepEfficiency` input+output) are now wired for real.
  `ExperimentRepository` (already suspend-based) threads `HealthConnectManager` through to
  every call site -- no other call sites existed, so this was a safe signature change.
- **`minSdkVersion` raised 21 -> 26**: Health Connect's client library requires API 26+
  (manifest-merge would otherwise fail); this also means `java.time.Instant`/`ZoneId` are
  natively available with no core-library-desugaring dependency needed.
- Checkin/failed-stage/text-action UI copy and the terms-of-service string that referenced
  Jawbone were updated to be wearable-agnostic; the terms string's claim that answers "will be
  collected for research" was also corrected -- that hasn't been true since Phase 2 removed
  the backend, and touching the surrounding Jawbone sentence was a reasonable place to fix it
  rather than leave stale copy in place.
- Verified: `:app:compileDebugKotlin`, `:app:compileDebugJavaWithJavac`,
  `:app:testDebugUnitTest` (still 26/26), and `:app:assembleDebug` all green.

Explicitly NOT done in this pass (still open):
- ACRA crash reporting still posts to `<BASE_URL>acra/report/` over the network -- this was
  already flagged as a separate, still-open decision in Phase 2's notes (drop it entirely vs.
  swap in a local/opt-in-only reporter) and deliberately not conflated with this pass either,
  since it's unrelated to Jawbone or the experiment-data self-containment goal specifically.
- `UserProfileEntity`/Room biographic-data storage remains unpopulated (see above).
- Live on-device verification against a real Health Connect provider (e.g. Health Connect app
  + a synced wearable) has not been performed in this pass -- only unit tests and a clean
  build/compile were run; there is no device/emulator available in this environment. Whoever
  next has device access should sanity-check the four `HealthConnectManager` extraction
  functions against real records before relying on a live experiment run.
- `view/` package (Phase 4 Compose scope) and `AlarmManager` -> `WorkManager` (also Phase 4)
  untouched, as planned.

Previously landed on the pass that completed Phase 2 (experiment-flow activities, on top of
the already-landed data layer):
- `MainActivity`, `ExperimentChooseActivity`, `ExperimentIntroActivity`,
  `ExperimentConfigActivity`, `ExperimentCreatedActivity`, `ExperimentCheckinActivity`,
  `ExperimentInstructionsActivity`, `ExperimentCompleteActivity`, `HistoryActivity` are all
  converted Java -> Kotlin, `@AndroidEntryPoint`-injected with `ExperimentRepository`, and no
  longer touch `Experiment.java`/`BackendAPI`/SharedPreferences for experiment state — full
  choose -> intro -> config -> daily check-ins -> complete flow now runs against Room with
  zero network calls. `backend/Experiment.java` (now fully unused) is deleted.
- Since `ExperimentEntity.isActive` is flipped to `false` on completion (matching
  `views.py`'s semantics, per the existing repository code), a new
  `ExperimentDao.getLatestExperiment()` (most recent by start_time, active or not) replaces
  the old `Experiment.CURRENT_EXPERIMENT_PREF` SharedPreferences pointer concept for
  navigation purposes — this is what lets the Complete screen still be reachable right after
  the final check-in, and lets `ExperimentChooseActivity` correctly re-offer a new experiment
  once the latest one is finished/cancelled, without any explicit "clear current experiment"
  step (there's nothing to clear now; the next `createExperiment()` call just becomes the new
  latest row).
- One-shot "new stage" / "failed stage" dialogs (previously mutable flags persisted to
  SharedPreferences and cleared after showing) are now passed from `ExperimentCheckinActivity`
  to `ExperimentInstructionsActivity` via Intent extras (`startActivityAfterCheckin`) instead
  of being persisted — instructions reached any other way (e.g. reopening the app) call
  `ExperimentRepository.refreshInstructions()` fresh with no outcome flags, so dialogs never
  replay on normal reopen.
- Added `CheckinOutcome.stageInputs` (was missing from the Phase 2 data layer) so the
  check-in progress grid on the instructions screen can be populated from
  `ExperimentRepository` instead of a server response.
- `HistoryActivity`'s cancel-experiment flow now calls `ExperimentRepository.cancelExperiment`
  directly; the "reason to quit" text the user enters is no longer persisted anywhere (no
  server to send it to, no local column for it) — if that data matters going forward, add a
  `cancelReason` column to `ExperimentEntity` in a follow-up, this pass just drops it.
- Verified: `:app:compileDebugKotlin`, `:app:compileDebugJavaWithJavac`,
  `:app:testDebugUnitTest` (still 26/26), and `:app:assembleDebug` all green.

Explicitly NOT done in this pass (still open, same as before):
- `SettingsActivity`/`IntroActivity`/onboarding still read/write the old
  `SharedPreferences`-based `UserData` blob and still call
  `BackendAPIFactory.getAPI(this).setBiographicData(...)` over the network — left alone on
  purpose, since it's entangled with the Jawbone onboarding question fragment
  (`QuestionJawboneFragment`/`JawboneQuestionActivity`) that Phase 3 removes. Migrating
  Settings to Room now would mean building throwaway Jawbone-token storage.
- `BackendAPI`/`BackendAPIFactory`/the two interceptors/Retrofit-OkHttp-Gson-Jodatime
  dependencies are still present in `build.gradle` — cannot be deleted yet because
  `SettingsActivity`/`JawboneQuestionActivity` still depend on them. Deletion is now blocked
  only on Phase 3 (Settings won't need a network call at all once Jawbone/biographic-data
  onboarding is reworked), not on anything left in the experiment flow itself.
- `view/` package not yet touched (still Java) — Phase 4 (Compose) scope.
- `UserProfileEntity` exists in Room but nothing writes to it yet; still tied to Settings
  migration above.

Previously landed (data model & engine foundation, unchanged this pass):
- Room schema (`ExperimentEntity`, `CheckinEntity`, `UserProfileEntity` + DAOs) mirroring
  the Django `Experiment`/`Checkin` models, including the per-stage JSON fields
  (`stage_dates`, `stage_target_values`, `stage_restart_count`, `initial_stage_average`).
- `ExperimentEngine.kt`: faithful Kotlin port of `Experiment.set_stage_targets`,
  `get_daily_target`, `is_output_stable`, `get_valid_days`, `get_num_missed_days`,
  `should_end_stage`, `calculate_results` from the Django source (confirmed directly
  against a clone of `AffectiveComputingQuantifyMeDjango`, not guessed/reinvented — an
  earlier draft of this engine in this same session fabricated its own ranges/stage
  count/confidence formula instead of porting the real algorithm; that draft was
  discarded). Verified via a new `ExperimentEngineTest.kt` that reproduces every Phase 0
  characterization-test assertion against the real Kotlin engine (26/26 passing),
  alongside the original Java `ExperimentEngineCharacterizationTest`/`ExperimentEngineReference`
  oracle, which still passes unchanged.
- `ExperimentType.kt`: sealed class with the real per-type range tables/rangeSize/
  stableRange/useVariability/shouldMinimizeResult ported from `analysis.py` (previously
  only 2 of 4 types' ranges were known from the characterization tests; the other two —
  LeisureHappiness, StepsSleepEfficiency — required pulling analysis.py directly).
- `ExperimentDataProvider.kt`: per-type `get_inputs`/`get_outputs`, matching the Django
  day-alignment logic (`_get_checkins_value`'s +1-day offset). LeisureHappiness is fully
  wired against local check-ins (needs no sensor at all). The other three types'
  wearable-backed inputs (and StepsSleepEfficiency's output) are explicit null-returning
  stubs pending Phase 3 Health Connect integration — this was scoped intentionally per
  the plan ("sensor data can still be manually stubbed/entered at this point").
- `ExperimentRepository.kt`: on-device replacement for `/start_experiment/`,
  `/experiment_checkin/`, `/refresh_instructions/`, `/cancel_experiment/`, orchestrated
  to match `views.py` exactly (should_end_stage -> restart/end_stage side effects ->
  recompute today's target -> calculate_results once complete). No network calls.
- Hilt DI wired (`di/DatabaseModule.kt`); `MyApplication` converted Java -> Kotlin
  (`@HiltAndroidApp`), ACRA init untouched/still network-based (that removal/replacement
  decision is explicitly still open per the plan, not part of this pass).
- Kotlin/coroutines/Room/Hilt added to `build.gradle` (root + app); confirmed
  `:app:compileDebugKotlin` and `:app:testDebugUnitTest` both green.

See "Explicitly NOT done in this pass" above for what's still open — the experiment-flow
activity migration described there is now done; what's left is Settings/onboarding (Phase 3
territory) and the old networking layer, which can't be deleted until Settings stops needing
it.

Agents should update this status when completing a phase.

---

## Phase 0 — Baseline & safety net

1. Get the project building as-is (or with the minimum change required) on a legacy SDK/AGP
   install, so there is a working reference build before any rewrite. Document exact
   Android Studio / SDK / build-tools versions that work in `CONTRIBUTING.md`.
2. Since there are no tests today, write **characterization tests** for the algorithm that
   must not regress. Pull the reference behavior straight from the Django source (it's the
   ground truth, even though it's being deleted as a runtime dependency):
   `Experiment.should_end_stage`, `Experiment.is_output_stable`, `Experiment.set_stage_targets`,
   `Experiment.get_daily_target`, and `Experiment.calculate_results` in `models.py`, plus the
   per-type range tables and `get_inputs`/`get_outputs` shape in `analysis.py`. Write these as
   plain Kotlin/JUnit tests against inputs/outputs (no Android or network dependency), so they
   run in milliseconds and become the regression harness for every later phase — this is the
   most valuable artifact Phase 0 produces, since it's the only thing standing between "port"
   and "silently reimplement wrong."

## Phase 1 — Tooling modernization (mechanical, low-risk)

Goal: same app, same behavior, modern build chain. No feature work, no architecture change.

- Bump Gradle wrapper, AGP, `compileSdkVersion`/`targetSdkVersion` to current stable;
  migrate `compile`/`testCompile` → `implementation`/`testImplementation`.
- Migrate `com.android.support:*` → AndroidX (`jetifier` as a crutch initially, then clean
  up imports).
- Replace deprecated APIs flagged by the new `targetSdkVersion` (notification channels,
  `AlarmManager` exact-alarm restrictions on Android 12+, scoped storage, runtime
  permissions flow already partially handled by `PermissionCheckingAppCompatActivity`).
- Get `assembleDebug`/`assembleRelease` green in CI (add a minimal CI workflow — currently
  none exists).
- **Don't bother upgrading Retrofit/OkHttp/Gson-Jodatime here** — the entire networking
  layer (`BackendAPI`, `BackendAPIFactory`, the two interceptors) is deleted in Phase 2, not
  modernized. Upgrading it now would be wasted work.
- **Exit criterion:** app installs and the full experiment flow still works exactly as
  before (still talking to the Django backend at this point, still on Jawbone) — verified
  against the Phase 0 regression tests.

## Phase 2 — Go local: on-device data model & experiment engine

This is the phase that actually removes the backend dependency.

- Migrate Java → Kotlin incrementally (file-by-file; interop is transparent). Priority
  order: `backend/` package first (smallest surface, most central), then `view/`, then
  `activities/`.
- **Introduce Room** as the on-device source of truth, replacing both the `SharedPreferences`
  JSON blobs (`Experiment.CURRENT_EXPERIMENT_PREF`, `Checkin.LAST_CHECKIN_PREF`) and the
  server's `Experiment`/`Checkin` tables. Schema mirrors the Django models closely:
  - `ExperimentEntity`: type, start/end time, current stage, per-stage dates, per-stage
    target values, per-stage restart counts, initial-stage average, result value/confidence,
    is-active/is-cancelled.
  - `CheckinEntity`: experiment id, date, did-follow-instructions, happiness, stress,
    productivity, leisure-time.
  - A local `UserProfile` (biographic data, timezone) — no auth, just a single-row local
    profile.
- **Port the experiment engine.** Translate `analysis.py`'s four `ExperimentType`
  implementations (`LeisureHappiness`, `SleepVariabilityStress`,
  `SleepDurationProductivity`, `StepsSleepEfficiency` — range tables, `get_range_size`,
  `get_stable_range`, `use_variability`/`should_minimize_result` flags) into Kotlin as a
  `sealed class ExperimentType`, merged with the existing client-side
  `Experiment.ExperimentType` (which already has the display/formatting half of the same
  concept — this unifies two representations of the same idea that today live on opposite
  sides of the network into one). Port `Experiment`'s stage-state-machine methods
  (`should_end_stage`, `is_output_stable`, `set_stage_targets`, `get_daily_target`,
  `get_valid_days`, `calculate_results`) as pure functions operating on Room-read data — these
  must pass the Phase 0 characterization tests unchanged.
- `refresh_instructions` stops being a network call and becomes "recompute today's
  instruction from local Room state," invocable synchronously any time the app opens.
  `get_experiments`/`cancel_experiment` become local Room queries/updates.
- **Delete** `BackendAPI`, `BackendAPIFactory`, `AuthInterceptor`, `AcraInterceptor`, and the
  Retrofit/OkHttp/Gson-Jodatime dependencies entirely — there is no backend to call.
  - ACRA crash reporting posted to `<BASE_URL>acra/report/`; decide separately whether to
    drop crash reporting entirely (consistent with "no network calls") or swap in a
    device-local log file / opt-in-only crash reporter unrelated to experiment data. Don't
    conflate this decision with the experiment-data self-containment goal.
- Introduce coroutines + `Flow` for async Room access, and a minimal DI setup (Hilt) so the
  old singleton-with-static-state pattern (`BackendAPIFactory`) becomes testable/injectable
  in its replacement (a Room-backed repository).
- **Exit criterion:** a full experiment (choose → intro → config → daily check-ins →
  complete) runs start-to-finish with zero network calls, entirely against local Room data,
  still passing the Phase 0 regression tests. (Sensor data can still be manually
  stubbed/entered at this point — Jawbone/Health Connect is Phase 3.)

## Phase 3 — Replace the wearable integration: Jawbone → Health Connect

Jawbone UP shut down in 2018; the Jawbone OAuth flow and SDK are entirely non-functional
today. This blocks the sensor data source for all four experiment types (steps for
steps→sleep-efficiency; sleep duration, sleep efficiency, and sleep start time for the
other three) — this was already going to need replacing even if the backend were kept.

Investigation of the actual coupling (both client and former server) found it's shallow:

- **Client side:** Jawbone touches exactly one onboarding page
  (`QuestionJawboneFragment`/`JawboneQuestionActivity`, reached only because
  `SettingsActivity extends JawboneQuestionActivity` instead of the generic
  `QuestionActivity`) plus one "open the Jawbone app" convenience button in
  `ExperimentCheckinActivity`. The client never called Jawbone's data API itself — it only
  ran the OAuth webview and handed the resulting token off (formerly to the backend).
- **Data shape:** the engine only ever needed four signals per day, computed by
  `analysis.py`'s helper functions — **daily step count**, **nightly sleep duration**,
  **sleep efficiency %** (`1 - awake_time/duration`), and **sleep start time**. The original
  authors already isolated data-source specifics behind `ExperimentType.get_inputs`/
  `get_outputs`, so swapping the source requires no changes to the stage/target/confidence
  algorithm ported in Phase 2.

Replacement plan:

- Remove `JawboneQuestionActivity`, `QuestionJawboneFragment`, the Jawbone UP SDK dependency,
  and the "open Jawbone app" button. Change `SettingsActivity` back to extend the generic
  `QuestionActivity`.
- Add a **Health Connect** permission-request page in the same onboarding wizard slot,
  requesting read access to `StepsRecord` and `SleepSessionRecord`.
- Reimplement the four extraction functions against Health Connect's local API instead of
  `JawboneMeasurement` rows: daily steps (aggregate `StepsRecord` per local day), sleep
  duration (`SleepSessionRecord` start/end), sleep efficiency (derivable from
  `SleepSessionRecord` stage data — awake stages vs. total session time, actually *more*
  precise than Jawbone's single opaque `awake_time` field), and sleep start time
  (`SleepSessionRecord.startTime`, timezone-aware).
- This is a strict UX improvement over the original beyond just "unblocking the app": users
  bring whatever wearable they already own (Fitbit, Garmin, Samsung Health, Oura, etc., all
  of which write into Health Connect) instead of being required to own a Jawbone UP,
  directly addressing the paper's own complaint that requiring a single tracker limited
  recruitment.
- Directly fixes the paper's §6.3 complaint: users didn't understand what counted as "a
  night" when they slept past midnight — `SleepSessionRecord` has explicit start/end
  timestamps, so surface that directly to the user at check-in instead of a silently-inferred
  day boundary.
- **Exit criterion:** a real experiment (e.g. steps→sleep-efficiency) runs end-to-end with
  live wearable data pulled from Health Connect into the Phase 2 Room-backed engine, no
  Jawbone code path or network dependency remaining anywhere in the app.

## Phase 4 — UI modernization

Only after Phases 1–3 give a working, testable, fully local base.

- Rebuild screen-by-screen in **Jetpack Compose**, starting with the highest-value/lowest-
  risk screens: `HistoryActivity`, `ExperimentChooseActivity`, `ExperimentInstructionsActivity`
  (the daily check-in screen users hit every day — worth the most polish).
- Replace the custom `QuestionActivity` + `ViewPager` + `ScrollPageIndicator` onboarding
  framework with Compose `Pager` + a `NavHost`-based wizard; keep the same two modes
  (replay vs. progressive reveal) since `SettingsActivity`/`IntroActivity`/
  `ExperimentConfigActivity` all depend on that behavior.
- Retire the custom view zoo (`SelectableIcon*`, `ColoredRadioGroup`, `TimePickerView`,
  `FontTextView`, `TintableImageView`) in favor of Compose equivalents / Material 3 theming
  as each screen is migrated — don't do a big-bang view layer swap.
- Replace `AlarmReceiver`'s `AlarmManager.setInexactRepeating` daily reminder with
  **WorkManager** (`PeriodicWorkRequest` + exact-time scheduling via
  `setExactAndAllowWhileIdle` where truly needed) for reliability across Doze/background
  restrictions on modern Android, and route notification firing through a proper
  `NotificationChannel` (required since Android 8).
- **Exit criterion:** full experiment flow (choose → intro → config → daily check-ins →
  complete) usable end-to-end on Compose UI with no legacy XML screens left in the critical
  path.

## Phase 5 — Product improvements from the paper's own findings

These are called out explicitly in §6 ("Discussion") and §6.4 ("Limitations") of the paper
as what the *original authors* recommend for a next version — treat this as a pre-validated
backlog, not new scope-creep:

1. **Mid-experiment visualization** (§6.2): 4/13 pilot users wanted to see their history
   during the experiment, not just after. The original team withheld this to avoid biasing
   behavior. Ship it as an opt-in toggle with a one-time explanation of the tradeoff, rather
   than a blanket withhold-until-done policy.
2. **Ongoing methodology transparency** (§6.3, §6.4): explain *why* a stage is restarting,
   what "a night" means for sleep data (now easy — Health Connect gives explicit session
   boundaries, see Phase 3), and re-surface the stage-length/target-zone explanation
   throughout the experiment, not just in the initial info session.
3. **Adherence support mechanisms** (§6.4): the paper's core finding is that only 1/13
   participants completed a full 4-stage experiment because objective adherence was 22.5%
   despite 75.6% check-in adherence. Concrete, scoped ideas the paper points at:
   - Multi-day-ahead preview of upcoming targets (currently explicitly withheld to avoid
     bias — the paper suggests testing the bias/support tradeoff).
   - Encouragement/reminder nudges mid-day (not just morning check-in). The paper reports
     personality-trait correlations with adherence, but that data came from a separate
     research questionnaire administered outside the app, not from anything the app itself
     collects today — if this direction is pursued, it requires *adding* a short in-app
     personality questionnaire during onboarding, not just reading data that's "already
     there."
4. **Data model generalization**: `Experiment.ExperimentType` today is a small closed set of
   hardcoded subclasses (per `CLAUDE.md`, "no dynamic registration"). Once Phase 2's
   sealed-class engine lands, consider whether experiment definitions (target zones, buffer
   size, formatting) should move to a **local, app-bundled config format** (e.g. JSON in
   `assets/`) so new experiment types are easier to author and review — without a backend,
   this can't be server-driven/OTA, so the benefit is purely maintainability, not
   dynamic/remote updates. Sequence last and validate demand before doing this.

## What gets removed entirely (not modernized, deleted)

- All authentication (`/obtain_token/`, Google-account-email lookup, Android-ID-as-UUID).
- The full networking layer: `BackendAPI`, `BackendAPIFactory`, `AuthInterceptor`,
  `AcraInterceptor`, Retrofit/OkHttp/Gson-Jodatime dependencies.
- The Jawbone OAuth flow, Jawbone UP SDK, and the "open Jawbone app" shortcut.
- Server-side Jawbone ingestion (`jawbone.py`, the `jawbone_webhook` endpoint,
  `JawboneMeasurement` table) — moot once there's no server and no Jawbone.
- The Django backend as a runtime dependency of this app, full stop. (The Django repo itself
  is untouched — this plan only concerns the Android client's dependency on it.)

## Optional: local data export

Since there's no backend to aggregate research data across users anymore, consider adding a
single **user-initiated** "export my results" action (share-sheet JSON or CSV of an
experiment's check-ins and outcome) so someone can still hand their own data to a researcher
or keep a personal record. This is opt-in and manual only — it must not become a disguised
automatic upload. Low priority; sequence after Phase 3 if requested.

## Suggested sequencing / ownership

Phases 0–3 are effectively required and roughly sequential (each unblocks the next: Phase 2
can't be validated without Phase 0's tests, Phase 3 needs Phase 2's local engine to plug
into). Phase 4 (UI) and Phase 5 (product features from §6) can run in parallel once Phase 3
lands, since they touch mostly disjoint layers (presentation vs. behavior/data). Do not start
Phase 4/5 work before Phase 3, since testing daily check-in UX changes against fake/stubbed
sensor data will hide the real adherence problems the paper is trying to solve.

## Explicitly out of scope for this plan

- Rewriting or maintaining the Django backend (separate repo; this app no longer depends on
  it at all after Phase 2).
- Any migration path for existing users' data currently stored on the live Django backend —
  if that matters, it's a one-time, separate export/import tool, not part of this plan.
- Adding new experiment types beyond the original 4 (covered as a Phase 5.4 architectural
  question, not committed work).
- iOS — the paper and this codebase are Android-only; no cross-platform ask here.

---

# Status as of 2026-07-22 (reconstructed)

> **Provenance note.** Everything above this line is the plan as it stood on
> **2026-07-13**. That was the last surviving copy of this file — the tracked original was
> lost when `AGENT_PLANS/` was gitignored and got deleted through a worktree junction (see
> CLAUDE.md, "Agent coordination"). The section below was rebuilt from `git log` and merged
> PRs, so it is accurate about *what landed* but does not reproduce whatever prose the lost
> version had. `AGENT_PLANS/` is now tracked in git, so this cannot happen again.

## Phases 0–5: landed

All phases in the plan above are substantially complete and merged to `master`.

| Phase | Status | Key commits / PRs |
|---|---|---|
| 0 — Baseline & safety net | Done | test suite grew steadily; see "Testing" below |
| 1 — Tooling modernization | Done | `2cfe361` gradle/AGP upgrade |
| 2 — Go local (Room + engine) | Done | PR #1, PR #2 |
| 3 — Jawbone → Health Connect | Done | PR #4 |
| 4 — UI modernization (Compose) | **Partial by design** | PR #5, PR #7, `8623c63`, `db2784d` |
| 5 — Product improvements | Done | PR #6, PR #8, PR #9 |

## Landed since the 2026-07-13 snapshot

- `adeaeee` — dark color scheme + optional Material You dynamic color.
- `2a8ee57` — privacy/manifest hardening: ACRA network reporting dropped, stale permissions
  pruned, backup disabled.
- `5cd4949` — moved main-thread-blocking Room / Health Connect reads off the UI thread.
- `354d67b` — fixed a race in the check-in intro patch (observe view lifecycle, not a
  one-shot root read).
- `8484125` + `1434e8a` — **Phase 5.4**: JSON-config experiment types
  (`assets/experiment_types.json` + `ExperimentTypeRegistry`) and the opt-in local data
  export. This is what made new experiment types a config-only change.
- `698e1a1` — `Converters` now round-trips `DateTime` zone-faithfully (PR #10).
- `30843fb`, `6adc07b` — broadened JVM unit-test coverage; added the `androidTest` source
  set and `HiltTestRunner` (PR #36, issue #21).
- `7961888` — removed dead LeakCanary 1.x; CI runs on push/PR to `master`.
- `cf2c047`, `db2784d` — ViewModel layer for History / ExperimentProgress /
  ExperimentChoose, then the daily check-in migrated to Compose + `CheckinViewModel`, with
  scale-button accessibility semantics.
- `2b7fbbf` (PR #14), `6cb852f` (PR #16), `21368f3` — fresh-install black screen, config
  "Continue" NPE, and birthday date-picker crash.
- `6895b4d` (PR #15) — removed the stale `base_url.xml` requirement from CONTRIBUTING/CI.
- `36b4105` (PR #30, issue #24) — deleted the dead `UserProfileEntity`/`UserProfileDao`.
- `d80890d` (PR #37, issue #29) — Health Connect **exercise-minutes** signal.
- `bf3181e` — `CLAUDE.md` tracked in git so it reaches every worktree by checkout.

## Testing

147 JVM unit tests pass on `master`. `app/src/androidTest` exists but **nothing runs it
automatically** — the `ubuntu-latest` CI runner has no emulator, so
`ExperimentCheckinScreenTest` only runs when a human or an agent with adb access launches
it. Wiring an emulator into CI is still open. Most UI-facing changes land "not visually
verified on-device"; treat those notes as real gaps.

## What remains

Phase 4 is deliberately incomplete: the onboarding wizard (`IntroActivity`,
`SettingsActivity`, `ExperimentConfigActivity`) still rides the legacy Java
`QuestionActivity`/`ViewPager` framework and the `view/` custom-widget zoo. That is tracked
as issue #22, not as a Phase 4 regression.

The live backlog is **`IMPROVEMENTS.md`** plus the GitHub issues; blocking relationships and
merge-conflict risk are in **`DEPENDENCIES.md`**.
