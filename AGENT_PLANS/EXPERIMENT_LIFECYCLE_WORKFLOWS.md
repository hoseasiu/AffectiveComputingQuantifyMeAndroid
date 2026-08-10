# Experiment lifecycle workflows — audit

**Date:** 2026-08-09. Prompted by two user reports in the same session: (1) opening the app
drops you straight into "Daily Check In" with no indication of what experiment you're even
in ([fixed separately](../app/src/main/java/edu/mit/media/mysnapshot/activities/ExperimentCheckinActivity.kt) —
the intro step now shows `ExperimentType.name`), and (2) a follow-up asking what happens if
you're not in an experiment at all, or want to pause/quit/switch. This file is the answer to
(2): every experiment-lifecycle workflow, traced through the actual code, with what's broken
and what's just missing. It complements `IMPROVEMENTS.md` (backlog/landed table) rather than
duplicating it — see that file's "Still open" table and §7.3 for the longer-lived items this
audit didn't resolve.

## How the app decides what screen to show you

Every cold entry into the app goes through `MainActivity`/`MainViewModel.route()`
([MainViewModel.kt](../app/src/main/java/edu/mit/media/mysnapshot/viewmodel/MainViewModel.kt)):

```
no experiment, or latest one is cancelled  -> ExperimentChooseActivity
latest experiment exists but isActive=false -> ExperimentCompleteActivity
latest experiment is active                 -> ExperimentCheckinActivity  (see note below)
```

**Note:** the third branch is supposed to be "check-in screen only if today's check-in isn't
done yet, otherwise straight to instructions" — but `MainActivity.FORCE_CHECKIN` is hardcoded
`true` (a debug flag explicitly preserved from the pre-Room implementation, per its own doc
comment and `CLAUDE.md`'s warning not to assume it's a bug). In the shipped app this makes the
third branch unconditional: **every cold open with an active experiment lands on the check-in
wizard, every time, even if you already checked in today.** The "already checked in -> go to
instructions instead" path is dead code in production; it only runs in tests that call
`route(forceCheckin = false)` directly.

This one flag is the root cause behind most of what follows, so it's called out once here
instead of repeated in every section below.

## Workflow 1 — "I'm not in any experiment"

**Works correctly, no changes needed.** `MainViewModel.route()`'s first branch sends you to
`ExperimentChooseActivity` whenever there's no experiment or the latest one was cancelled.
That screen (`ExperimentChooseActivity.kt`) lists the seven built-in types plus any
user-authored custom ones, with a "Create Your Own Experiment" card. Selecting one walks you
through `ExperimentIntroActivity` -> `ExperimentConfigActivity` -> `ExperimentCreatedActivity`.
No dead ends found here.

## Workflow 2 — "I want to pause"

**Does not exist, at any level.** Searched the whole engine/repository/UI layer for "pause" —
zero matches. `ExperimentEntity` only has `isActive`/`isCancelled` booleans
([ExperimentEntity.kt](../app/src/main/java/edu/mit/media/mysnapshot/database/ExperimentEntity.kt));
`ExperimentEngine`'s stage state machine has no notion of a suspended/frozen day. The closest
substitute today is **turning off check-in reminder notifications** in `SettingsActivity`'s
notification step (a plain enable/disable checkbox) — that silences the nudge, but the
experiment keeps running underneath: missed days still count against you via
`ExperimentEngine.getNumMissedDays`/stage-restart logic. It is not a real pause, just muting
the reminder.

A real pause (freeze the current stage's day-count while paused, resume where you left off)
would be an `ExperimentEngine`/Room-schema change — squarely the kind of thing `CLAUDE.md`
calls "a product decision" requiring engine-test coverage, not a drive-by fix. **Left
unimplemented, filed as an open question below** rather than guessed at.

## Workflow 3 — "I want to quit"

**Exists, but was unreachable from the screen most users are actually on.** The real feature:
`HistoryActivity`'s `ExperimentCard` shows a "Quit" button on the active experiment, which opens
`CancelExperimentDialog` ("Really Stop Experiment? All your progress will be lost forever!"),
requires typing a non-empty "Reason to Quit", and on confirm calls
`HistoryViewModel.cancelExperiment()` -> `ExperimentRepository.cancelExperiment()`, which sets
`isActive=false, isCancelled=true`. `MainViewModel.route()`'s first branch then correctly routes
you to `ExperimentChooseActivity` next time you open the app. The state machine itself is
sound.

Two real problems found:

1. **Reachability.** `HistoryActivity` is only linked from a header icon
   (`HeaderIcon(R.drawable.button_home, "History", ...)`) on `ExperimentInstructionsActivity`,
   `ExperimentCompleteActivity`, and `ExperimentProgressActivity`. **`ExperimentCheckinActivity`
   has no header icons at all** — no Settings, no History, nothing. Combined with
   `FORCE_CHECKIN` always routing you into check-in on open (see above), a user who wants to
   quit has no menu, button, or gesture to get there without first finishing that day's entire
   6-question check-in wizard. That's the concrete "workflow is off" feeling reported.
2. **The "Reason to Quit" you're required to type is thrown away.** `CancelExperimentDialog`
   validates the field is non-empty, then calls `onConfirm()`, which calls
   `onCancelConfirmed(experiment)` — **the `reason` string itself is never passed anywhere.**
   `HistoryViewModel.cancelExperiment(experiment)` and
   `ExperimentRepository.cancelExperiment(experimentId)` both take no reason parameter, and
   `ExperimentEntity` has no column to hold one. This is leftover copy from when the old Django
   backend collected cancellation reasons as research data (`"please let us know why"` — a
   participant-facing research prompt); post-modernization there's no backend to send it to and
   no local column to keep it in, so the app makes you type something and then discards it
   unread. Confirmed by reading the full call chain, not just the dialog.

## Workflow 4 — "I want to change experiments"

**Only via quitting.** There is no non-destructive "switch experiments" — you cancel the
current one (Workflow 3, "all progress lost forever," no undo) and then `ExperimentChooseActivity`
naturally comes up next open. `IMPROVEMENTS.md` §7.3 (#27, "support multiple concurrent
experiments") is the tracked long-term fix for wanting more than one experiment without
destroying either; that's out of scope here (schema + engine change, already tracked). This
audit's fix is narrower: make the destructive "quit and pick again" path actually reachable and
honest about what it does (Workflow 3's two problems above).

## Workflow 5 — other things found while tracing these paths

- **Finishing an experiment is handled well.** `ExperimentCompleteActivity` shows the result and
  has explicit "choose a new experiment" / Settings / History buttons — no dead end there.
- **First-day-of-experiment placeholder screen is handled well.** `ExperimentInstructionsActivity`'s
  `FirstDayScreen` (shown before any check-in has happened) already has Settings + History
  header icons — it's exactly the pattern the check-in screen was missing.
- **Repeated same-day check-in submissions look possible but are out of scope for this pass.**
  Because `FORCE_CHECKIN` always sends an active experiment straight to the check-in wizard, and
  `CheckinViewModel.load()`/`checkValidity()` never checks "did I already check in today,"
  nothing stops a user from completing the wizard a second time in one day if they reopen the
  app after already checking in — `ExperimentRepository.submitCheckin()` is a plain
  `checkinDao.insert()` with no per-day uniqueness guard. This is adjacent to, but distinct
  from, the navigation gap this pass fixes, and touches the same `ExperimentEngine`/stage-day
  accounting `CLAUDE.md` flags as a product decision. **Flagged, not fixed here** — see Open
  questions.

## Changes made in this pass

1. **Check-in wizard intro now names the experiment** (already landed earlier this session) —
   [ExperimentCheckinActivity.kt](../app/src/main/java/edu/mit/media/mysnapshot/activities/ExperimentCheckinActivity.kt)'s
   `IntroStep` shows `experimentType.name` above the generic "Daily Check In" title.
2. **Settings + History header icons added to the check-in wizard**, matching the exact
   `FirstDayScreen` pattern (`button_profile`/`button_home`, untinted). This is the direct fix
   for Workflow 3's reachability problem: since `FORCE_CHECKIN` means check-in is where most app
   opens land, that screen now always has a way out to Settings (where notifications can be
   turned off — the closest thing to "pause" that exists) and History (where Quit/cancel and
   JSON export live), without touching the `FORCE_CHECKIN` flag itself.
3. **`CancelExperimentDialog` no longer demands a reason it discards.** Replaced the required
   "Reason to Quit" text field with a plain, honest confirmation
   ("This can't be undone — all your check-in progress for this experiment will be deleted.").
   No schema change, no behavior change to `cancelExperiment()` itself — just stopped asking
   users to type something into a void.

## Open questions (not resolved by this pass — for the user/maintainer to decide)

- **Should `FORCE_CHECKIN` be flipped to `false`?** That would restore "already checked in today
  -> go straight to instructions" as the real behavior, which independently fixes the duplicate
  check-in risk in Workflow 5 and makes the check-in screen a true once-a-day gate again. Left
  alone here because `CLAUDE.md` explicitly flags it as intentional, preserved debug behavior
  and warns against assuming otherwise without reading the comment at its declaration
  ([MainActivity.kt](../app/src/main/java/edu/mit/media/mysnapshot/activities/MainActivity.kt)) —
  this is a call for whoever owns that decision, not something to silently flip.
- **Should the cancellation reason be persisted instead of dropped from the UI entirely?** Doing
  that properly needs a Room migration (new column on `ExperimentEntity`, DAO/exporter updates,
  migration test) — bigger surface area than this pass's scope. Noted as a possible follow-up,
  not started.
- **Real "pause" support** (Workflow 2) would need engine-level day-accounting changes and is
  explicitly out of scope for a drive-by fix per `CLAUDE.md`'s guidance on `ExperimentEngine`
  changes being product decisions.
