# Contributing / local build setup

This project is a legacy (2016-2018) Android codebase being modernized per
[AGENT_PLANS/MODERNIZE.md](AGENT_PLANS/MODERNIZE.md). This document covers what you need
to build it today, plus notes on tooling decisions made during Phase 0/Phase 1 of that plan.

## Toolchain that is known to work

- JDK 17
- Android SDK platform 35, build-tools 34.0.0/35.0.0 (auto-installed by AGP if missing;
  license must be accepted once, e.g. via Android Studio's SDK Manager)
- Gradle 8.9 (via `./gradlew` / `gradlew.bat` — do not use a different Gradle install)
- Android Gradle Plugin (AGP) 8.6.1

Windows note: `./gradlew` under Git Bash may fail to resolve `JAVA_HOME` (a
`cygpath`/MSYS quirk in the wrapper's shell script, not specific to this project). If that
happens, run `gradlew.bat` from PowerShell/cmd instead.

## Required local config before building

1. **`local.properties`** (repo root, gitignored): `sdk.dir=<path to your Android SDK>`.
2. **`keystore.properties`** (repo root, gitignored) — optional. If present, must define
   `storeFile`, `storePassword`, `keyAlias`, `keyPassword` for a real release-signing
   keystore. If absent, `assembleRelease` falls back to signing with your local Android
   debug keystore (`~/.android/debug.keystore`) so the build still succeeds (e.g. in CI or
   on a fresh checkout) — that fallback produces an installable APK but **is not suitable
   for an actual store listing**. Set up a real `keystore.properties` before shipping.

## Running CI checks locally

`.github/workflows/android-ci.yml` (`testDebugUnitTest`, `assembleDebug`, `assembleRelease`)
needs no backend or GitHub-specific infra — it's just Gradle against local config, so run the
same commands directly instead of pushing to check:

```powershell
.\gradlew.bat testDebugUnitTest --console=plain
.\gradlew.bat assembleDebug --console=plain
.\gradlew.bat assembleRelease --console=plain
```

Prerequisites (see above): `local.properties` with `sdk.dir`. `assembleRelease` falls back to
your `~/.android/debug.keystore` if you have no `keystore.properties`, which Android Studio
normally creates on first build/run.

## Phase 0 note: why there's no literal "build on the original 2016 toolchain" step

Phase 0 of the modernization plan calls for getting the project building as-is (AGP 2.2.0,
`compileSdkVersion 23`) as a safety-net baseline before any rewrite. That was evaluated and
found infeasible to do faithfully in this environment, for reasons independent of this
codebase:

- **jcenter/Bintray shut down in 2021** and no longer serves packages. Nearly every
  dependency in the original `app/build.gradle` (`com.android.support:*`,
  `com.squareup.retrofit:retrofit:2.0.0-beta2`, `com.squareup.okhttp:okhttp:2.4.0`,
  `com.nineoldandroids`, `com.flyco.pageindicator`, the `daimajia`/`leonids`/`500px`
  UI-flourish libraries, `gson-jodatime-serialisers`, etc.) was hosted there. A build
  against the *original* `build.gradle`, unmodified, cannot resolve dependencies today on
  any machine, not just this one.
- The build environment available here has Android SDK platforms 35/36 and build-tools
  34-36 installed, with no `cmdline-tools`/`sdkmanager` present to install the original
  `compileSdkVersion 23` platform or `buildToolsVersion 22.0.1`.

Given the plan's own allowance to do this "with the minimum change required," Phase 0's
baseline-verification step and Phase 1's toolchain modernization were done as one pass:
the safety net is the **characterization test suite**
(`app/src/test/java/edu/mit/media/mysnapshot/backend/ExperimentEngineCharacterizationTest.java`,
ported by hand from the Django backend's `models.py`/`analysis.py` — see that file's javadoc),
not a build using 2016-era tooling. `./gradlew assembleDebug`, `assembleRelease`, and
`testDebugUnitTest` are all green on the toolchain above.

## Phase 1 dependency changes (mechanical, forced by the jcenter shutdown)

Everything below preserves existing behavior; nothing here is a feature change. Grouped by
why the original coordinate/version couldn't just be bumped in place:

**Vendored directly into source, later deleted** (`app/src/main/java/com/flyco/pageindicator/...`):
`com.flyco.pageindicator:FlycoPageIndicator_Lib` (used by `view/ScrollPageIndicator.java`,
the onboarding-wizard page-dot indicator). Neither the original jcenter artifact nor a
jitpack build of it is available — jitpack's own build of that repo fails outright against
its ancient Gradle config (verified via jitpack's build-status API). The library is MIT
licensed (github.com/H07000223/FlycoPageIndicator); its ~950 lines were copied in unchanged
except for `android.support.v4.view.ViewPager` → `androidx.viewpager.widget.ViewPager` and
pointing its `R.styleable` references at this app's own `R` class (its `attrs.xml` was
merged into `app/src/main/res/values/attrs.xml`). Issue #22 (onboarding wizard → Compose)
deleted `ScrollPageIndicator` along with the rest of the legacy `view/`/`activities/questions/`
zoo, and with it the entire vendored `com.flyco.pageindicator` package and its `attrs.xml`
entries — it had no other callers.

**Removed — confirmed zero references anywhere in the codebase** (verified by grepping
`app/src/main/java` and `app/src/main/res`, not assumed):
`com.google.android.gms:play-services-location`, `com.google.android.gms:play-services-identity`
(the app's actual Google-account lookup, `BackendAPIFactory.getEmail()`, uses the plain
framework `android.accounts.AccountManager`, not any Play Services API — these two
dependencies, and the manifest's `com.google.android.gms.version` meta-data that only
existed to support them, were already inert), `com.plattysoft.leonids:LeonidsLib`,
`com.daimajia.easing:library`, `com.daimajia.androidanimations:library`,
`com.github.500px:500px-android-blur`, `io.nlopez.smartlocation:library`,
`pl.droidsonroids.gif:android-gif-drawable`.

**Version-bumped to a Maven-Central-hosted release, same library/behavior**:
`nineoldandroids`, `com.makeramen:roundedimageview`, `joda-time`, `com.squareup.picasso`,
`ch.acra:acra`, `gson-jodatime-serialisers`.

**Deliberately left on their original ancient versions**: Retrofit 2.0.0-beta2, OkHttp 2.4.0,
Gson 2.3.1, the Jawbone UP SDK. Per the modernization plan, the entire networking layer and
the Jawbone integration are deleted (not upgraded) in Phase 2/3, so upgrading them now would
be wasted work.

**Forced upgrade (not optional) — ACRA**: `ch.acra:acra:4.9.0/4.9.2` crashes on launch on
current Android (`NoClassDefFoundError` on `com.android.internal.util.Predicate`, a hidden
platform class `ACRA.getCurrentProcessName()` reflects into that no longer exists). This
isn't a jcenter-availability problem like the others above — 4.9.2 resolves fine from Maven
Central, it's just incompatible with the OS itself now. Replaced with `ch.acra:acra-http:5.11.3`,
whose config API is a full rewrite (`CoreConfigurationBuilder`/`HttpSenderConfigurationBuilder`,
both `@AutoDsl`-generated, in place of the old hand-written `ConfigurationBuilder`/
`ACRAConfiguration`). See `MyApplication.attachBaseContext` — same destination URL, same
basic-auth/JSON/POST behavior, just the new builder shape. (Two research passes on this
guessed slightly wrong method names for the generated builders; what's checked in was
verified by decompiling the actual `acra-core`/`acra-http` 5.11.3 AARs with `javap` rather
than trusted from docs/wiki snapshots — if you touch this again, do the same rather than
guessing from ACRA's wiki, which lags the generated API.)

## Phase 1 targetSdk-driven fixes

Raising `targetSdkVersion` to 35 (from 23) surfaces real behavioral requirements, not just
compiler warnings:

- **`PendingIntent` mutability**: both `PendingIntent.getActivity`/`getBroadcast` calls in
  `notifications/AlarmReceiver.java` now OR in `FLAG_IMMUTABLE` — required since API 31, or
  the app crashes with an `IllegalArgumentException` at the call site.
- **Notification channel**: `AlarmReceiver.createNotification` now creates (or reuses) a
  `NotificationChannel` on API 26+ and uses the channel-aware `NotificationCompat.Builder`
  constructor. Without a channel, notifications silently never appear on API 26+ (no crash,
  no error — they just don't show).
- **Explicit `android:exported`**: mandatory since API 31 on any manifest component with an
  intent-filter. Added to `MainActivity`, the Jawbone `OauthWebViewActivity`, and (via a
  `tools:node="merge"` override, since it comes from a third-party AAR whose own manifest
  predates this requirement) LeakCanary 1.3.1's `DisplayLeakActivity`.
- **`AlarmManager`**: already used `setInexactRepeating` for the daily check-in reminder, so
  the API 31+ exact-alarm scheduling restriction (`SCHEDULE_EXACT_ALARM`) does not apply —
  no change needed there.
- **Scoped storage**: not applicable — grepped for `Environment.*`, `MediaStore`,
  `FileOutputStream`, `FileProvider` across `app/src/main/java` and found no direct external
  storage access. `PermissionCheckingAppCompatActivity`'s `WRITE_EXTERNAL_STORAGE` runtime
  prompt is not backed by any actual file write today; left as-is (not a targetSdk-blocking
  issue, so out of scope for a "no feature work" pass — flagged here for whoever picks up
  Phase 4/5 UI work).

## Known verification gap

The app still talks to the Django backend at this stage of the plan (that dependency isn't
removed until Phase 2). This environment has no running instance of
[`AffectiveComputingQuantifyMeDjango`](https://github.com/mitmedialab/AffectiveComputingQuantifyMeDjango)
to point `BASE_URL` at, so Phase 1's exit criterion was verified as far as it could be
without one: installed the debug APK on a `Medium_Phone_API_36.0` emulator and manually
walked `MainActivity` → `IntroActivity` (runtime `GET_ACCOUNTS` permission prompt, granted)
→ `SettingsActivity`'s onboarding wizard (`QuestionActivity` + `ViewPager` +
`ScrollPageIndicator`, i.e. the vendored FlycoPageIndicator code path) with no crashes at
any step. The login/experiment-sync network flows themselves were **not** exercised
end-to-end — that requires a live backend and is moot anyway once Phase 2 removes the
backend dependency entirely.

## Phase 3 — Jawbone → Health Connect

`minSdkVersion` was raised from 21 to 26 as part of this phase: `androidx.health.connect:
connect-client` requires API 26+, and its own manifest's `minSdkVersion` would otherwise
conflict with this app's during manifest merging. This also means `java.time.Instant`/
`ZoneId` (used in `HealthConnectManager`) are natively available on all supported devices —
no core-library-desugaring dependency was needed.

**Known verification gap**: the four `HealthConnectManager` extraction functions (daily
steps, sleep duration, sleep efficiency, sleep start time) were verified by unit tests
against the engine (`ExperimentEngineTest`, still 26/26) and a clean `assembleDebug`, but
**not** exercised against a real Health Connect provider — this dev environment has no
Android device/emulator with Health Connect installed and a wearable synced to it. Before
relying on a live experiment run, sanity-check `HealthConnectManager`'s per-day aggregation
against actual `StepsRecord`/`SleepSessionRecord` data on a real device.
