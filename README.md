# QuantifyMe Android App

Many health recommendations aren't tuned to a specific individual. Maybe you should sleep 9 hours a night instead of the recommended 8, or walk 8,000 steps a day to be your best. A scientific way to find personalized recommendations and causal links is to conduct experiments using single-case experimental design; however, properly designed single-case experiments are not easy!

QuantifyMe walks you through a self-experiment: pick a question (e.g. "how does my sleep duration affect my productivity?"), get a daily behavioral target for four staged phases, check in once a day, and see which target level actually produced the best outcome for you.

The four built-in experiments:

* How does my leisure time affect my happiness?
* How do inconsistent bedtimes affect my stress level?
* How does my nightly sleep affect my productivity?
* How does my activity level affect my sleep efficiency?

## This fork vs. the original

This started as the MIT Media Lab research app described in the papers below, and has since been substantially modernized and rearchitected. The biggest change: **the app is now fully self-contained on-device — there is no backend, no account, and no network calls.** Everything that used to happen server-side (the adaptive stage/target/confidence algorithm, experiment and check-in storage) now runs locally.

Other notable changes since forking:

* **No server, no login.** The companion Django backend (`AffectiveComputingQuantifyMeDjango`) is no longer used at all; all state lives in an on-device Room database.
* **Health Connect instead of Jawbone UP.** The Jawbone UP wearable platform shut down in 2018; step count, sleep duration/efficiency, and sleep start time now come from [Health Connect](https://developer.android.com/health-and-fitness/guides/health-connect), so any wearable that writes into Health Connect (Fitbit, Garmin, Samsung Health, Oura, etc.) works.
* **Jetpack Compose UI** with a `@HiltViewModel` per screen, replacing the original XML/`Activity`/`Fragment` screens. Dark theme and optional Material You dynamic color are supported.
* **Kotlin + Room + Hilt + WorkManager**, replacing the original Java + `SharedPreferences` + `AlarmManager` stack. Notifications (daily check-in reminder, mid-day adherence nudge) are scheduled via `WorkManager` so they survive Doze/reboots correctly.
* **Experiment types are data-driven**, defined in [`app/src/main/assets/experiment_types.json`](app/src/main/assets/experiment_types.json) rather than hardcoded subclasses, so new experiments can be added without touching the engine.
* **Product improvements pulled from the original paper's own "future work" section**: opt-in mid-experiment progress visualization, explanations of *why* a stage restarted and what counts as "a night" for sleep data, a multi-day-ahead target preview, and a mid-day encouragement notification — all things the original team identified as worth trying but didn't ship.
* **User-initiated data export**: from the History screen, export any experiment's check-ins and result as JSON via the system share sheet. This is the only way data ever leaves the device — there is no automatic upload.
* **Privacy/manifest hardening**: no crash-reporting network calls, no unused permissions, backups disabled.
* Broader automated test coverage: the original experiment engine is covered by characterization tests ported directly from the Django backend's algorithm, plus JUnit/Robolectric tests for the Room DAOs, repository, notification scheduler, and ViewModels.

See [`AGENT_PLANS/MODERNIZE.md`](AGENT_PLANS/MODERNIZE.md) for the full phase-by-phase history of this rewrite, including what's still open.

The accompanying (now-unused) backend can still be found here, for reference:
https://github.com/mitmedialab/AffectiveComputingQuantifyMeDjango

## Getting started

Requirements: JDK 17, Android SDK platform 35 (auto-installed by AGP if missing), and `sdk.dir` set in a local `local.properties` file.

```powershell
.\gradlew.bat assembleDebug --console=plain
.\gradlew.bat testDebugUnitTest --console=plain
```

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for the full local build setup, including required gitignored config files, Windows-specific `gradlew` notes, release signing, and the reasoning behind various dependency/tooling decisions made during modernization.

## Architecture

* `engine/` — the ported single-case-experiment algorithm (stage sequencing, target computation, stability/restart rules, confidence calculation), driven by `experiment_types.json`. Pure Kotlin, no Android dependency.
* `data/` — `ExperimentRepository` (the on-device replacement for the old REST API) and `ExperimentExporter`.
* `database/` — Room entities and DAOs (`ExperimentEntity`, `CheckinEntity`).
* `health/` — `HealthConnectManager`, wrapping Health Connect reads for steps and sleep.
* `activities/` + `viewmodel/` — the Compose screens and their `@HiltViewModel`s (choose experiment → intro → config → daily check-in → complete/history/progress).
* `notifications/` — `WorkManager`-based daily check-in reminder and mid-day adherence nudge.
* `ui/theme/` — Compose theme (colors, typography, dark/dynamic color).

## Testing

```powershell
.\gradlew.bat testDebugUnitTest --console=plain
```

Runs the JVM/Robolectric test suite: the experiment-engine characterization tests (verified against the original Django algorithm), Room DAO tests, repository/export tests, notification-scheduler tests, and ViewModel tests. There's no CI-run instrumented/UI test suite — anything requiring a real device or emulator (Health Connect reads, notification firing, on-screen behavior) needs manual verification, per the notes in `AGENT_PLANS/MODERNIZE.md`.

## Privacy

The app makes no network calls of any kind during normal use. All experiment and check-in data stays in the local Room database unless you explicitly use the "export" action on an experiment, which hands a JSON file to the system share sheet for you to send wherever you choose.

## Background & citation

More information about the original project and lessons learned can be found in the papers this app is based on:

* Sano, A., Taylor, S., Ferguson, C., Mohan, A., Picard, R. "QuantifyMe: An Automated Single-Case Experimental Design Platform," In Proc. International Conference on Wireless Mobile Communication and Healthcare (MobiHealth), Vienna, Austria, November 2017. [PDF](http://affect.media.mit.edu/pdfs/17.sanotaylor_etal_quantifyme.pdf)
* Taylor, S., Sano, A., Ferguson, C., Mohan, A., Picard, R. "QuantifyMe: An Open-Source Automated Single-Case Experimental Design Platform," Sensors, April 2018. [PDF](https://dam-prod.media.mit.edu/x/2018/04/05/18.TaylorSano-QuantifyMe.pdf)

## Original Authors
* Craig Ferguson
* Sara Taylor
* Akane Sano

## License
The MIT License (MIT)

Copyright (c) 2018 MIT Media Lab

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
