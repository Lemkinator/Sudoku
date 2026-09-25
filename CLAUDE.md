# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK (requires keystore)
./gradlew lint                   # Run Android Lint
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run instrumented tests (device required)
./gradlew spotlessCheck detekt   # Static analysis (formatting + code smells)
```

Debug builds append `.debug` to the app ID — can be installed alongside release.

One-time per clone, opt into the pre-commit hook (runs Spotless + Detekt before each commit):

```bash
git config core.autocrlf input    # Windows only
git config core.hooksPath .githooks
```

Missing this opt-in is a top build-failure cause for new contributors — CI enforces the same checks the hook runs locally.

## Private Dependencies (Required for Build)

Two private GitHub Maven repos are used:

- `https://maven.pkg.github.com/tribalfs/oneui-design`
- `https://maven.pkg.github.com/lemkinator/common-utils`

Provide credentials via **one** of these (checked in order):

1. `github.properties` in project root: `ghUsername=...` / `ghAccessToken=...`
2. `~/.gradle/gradle.properties`: `ghUsername=...` / `ghAccessToken=...`
3. Env vars: `GH_USERNAME` / `GH_ACCESS_TOKEN`

### Baseline Profile & Benchmarks

The baseline profile is generated automatically as part of every `assembleRelease` — no manual step
and nothing committed to git (`app/build.gradle.kts`'s `baselineProfile { variants { create("release") { ... } } }`).
PR CI passes `-Pandroidx.baselineprofile.skipgeneration` so a PR's `assembleRelease` never boots the
GMD; the release workflow and a weekly smoke test (`baseline-profile.yml`) don't, so they always
generate fresh. `./gradlew :app:generateBaselineProfile` still works standalone as a local diagnostic
(same GMD device — image already cached if you ran instrumented tests) — run it in the background,
not a foreground shell with a short timeout; it takes ~9-10 minutes:

```bash
./gradlew :app:generateBaselineProfile -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect
```

Run macrobenchmarks manually on a **connected physical device**, never the GMD (the library flags an
emulator as an `EMULATOR` error condition) — never in CI, only after touching the startup path or a
benchmarked journey:

```bash
./gradlew :benchmarks:connectedBenchmarkReleaseAndroidTest
```

Read the delta between `startupBaselineProfile` and `startupNoCompilation` (same device, so noise
cancels) — that delta is what the profile is worth. Don't chase absolute ms, don't gate on them,
don't store history.

## Architecture

Clean Architecture with three layers. Activities/Fragments obtain a `@HiltViewModel`-annotated ViewModel via `by viewModels()`, which owns
the use-case injections; screens with state/events expose them via `StateFlow`/`Channel<Event>`.

```text
de.lemke.sudoku/
├── ui/           # Activities, Fragments, custom views, RecyclerView adapters, ViewModels
├── domain/       # Use cases + domain models (Sudoku, Field, Position, Difficulty)
└── data/         # Room DB + SharedPreferences-backed settings, mappers, repositories
```

**Data flow:** UI → ViewModel → UseCase → Repository → Room/SharedPreferences. Reactive updates via `Flow<>`. Background work via
`withContext(dispatcher)`, where `dispatcher` is a Hilt-injected `CoroutineDispatcher` (see `di/DispatchersModule.kt` below) — never call
`Dispatchers.IO`/`.Default`/`.Main` directly in use cases.

**Domain models:** `Sudoku` (4×4/9×9/16×16), `Field` (cell with solution/value/notes), `Position` (row/col/block), `Difficulty` (
VERY_EASY…EXPERT). Game logic lives on the domain objects themselves (`move()`, `setHint()`, `errorLimitReached()`).

**Sudoku modes:** Normal (modeLevel = 0), Level (modeLevel > 0), Daily (modeLevel = -1).

## Key Patterns

**Hilt DI:** `@HiltAndroidApp` on `App`, `@AndroidEntryPoint` on Activities/Fragments. All modules live in `di/`: `PersistenceModule`
provides singleton `AppDatabase`; `DispatchersModule` provides `@IoDispatcher`/`@DefaultDispatcher`/`@MainDispatcher`-qualified
`CoroutineDispatcher`s (qualifiers from `common-utils`) for injection into use cases instead of hardcoding `Dispatchers.*`.

**Use cases:** Single-responsibility, `@Inject` constructor. Return domain types or `Flow<>`. Named with action-verb field names (parent
CLAUDE.md convention).

**Room:** Two entities (`SudokuDb`, `FieldDb`). Schema exported to `app/schemas/`. Bidirectional mappers in `data/database/`.

**Settings:** All user preferences stored via `UserSettings : SettingsRepository`, a SharedPreferences-backed implementation from the
`common-utils` library, constructor-injected into ViewModels. `di/SettingsModule.kt` provides it via Hilt. Daily sudoku notifications
scheduled via `AlarmReceiver`.

**ViewModels:** One `@HiltViewModel`-annotated ViewModel per Activity/Fragment. Screen state (where present) as `StateFlow<UiState>` using
Kotlin's explicit-backing-field style; one-shot navigation/toast/finish events as `Channel<Event>(BUFFERED).receiveAsFlow()`.

## Notable Dependencies

- `dev.oneuiproject:oneui-design` — Samsung OneUI UI components (GitHub Maven repo)
- `de.sfuhrm:sudoku` — Sudoku generation algorithm
- `com.google.dagger:hilt-android` — DI
- `androidx.room` — Persistence
- `com.google.android.gms:play-services-games-v2` — Play Games achievements/leaderboards
- `io.kjson:kjson` — JSON serialization for import/export

`app/src/main/res/values/games-ids.xml` is the verbatim Play Console export; never edit it.

## Static Analysis

Four tools run as part of `./gradlew build`:

- **Spotless** — enforces formatting via ktlint (sole ktlint driver;
  Detekt has no ktlint wrapper). Fix violations with
  `./gradlew spotlessApply`.
- **Detekt** — static analysis; config at `config/detekt/detekt.yml`.
  `autoCorrect = false` — fixes are manual.
- **Kover** — coverage floor enforced via `minBound` in `app/build.gradle.kts`'s
  `kover { reports { variant("debug") { verify { rule { ... } } } } }`.
  Verify: `./gradlew koverVerifyDebug`.
- **Konsist** — architecture rules in
  `app/src/test/java/de/lemke/sudoku/ArchitectureTest.kt`. Enforces
  `data/domain/ui` layering. Runs as part of `./gradlew test`.

**ktlint rule overrides** — two rules disabled in `.editorconfig` to match
community practice (NowInAndroid, Pokedex both use the inline form):

- `ktlint_standard_annotation = disabled` — ktlint 1.7+ moves `@Inject`
  before `constructor` onto its own continuation line, doubly-indenting
  the class body (8 sp instead of 4 sp).
- `ktlint_standard_class-signature = disabled` — in ktlint 1.7+, both
  rules together enforce the split form; disabling only `annotation` is
  insufficient.

## Robolectric + JUnit 5

See the shared Robolectric/JUnit 5 policy in `A:\repo\android\CLAUDE.md`. This repo defaults to
JUnit 5 (Kotest runs on the JUnit 5 platform — see `ArchitectureTest.kt`); JUnit 4 +
`junit-vintage-engine` is used only for tests that need Robolectric (screenshot tests, Hilt
activities, Context-backed settings/use cases).

## Settings in Tests

Tests never mock settings: every test uses the real `UserSettings` over an isolated, empty
store, so defaults come from `UserSettings`'s own production delegates — no duplicated default
values, no manual reset helpers, no per-field mock stubs.

The only canonical way to get a fresh store in a test is `freshTestPreferences()` — published by
common-utils from `lib/src/testFixtures` (`testImplementation(testFixtures(libs.common.utils))` /
`androidTestImplementation(testFixtures(libs.common.utils))`). It returns a UUID-named
`SharedPreferences` file, fresh by construction. Test code never calls
`getSharedPreferences(...)` or `PreferenceManager.getDefaultSharedPreferences(...)` directly.

- **`TestSettingsModule`** — `app/src/testFixtures/java/de/lemke/sudoku/TestSettingsModule.kt` —
  `@TestInstallIn`-replaces the production settings module with `UserSettings` over
  `freshTestPreferences(context)`, visible to both `src/test` and `src/androidTest` via AGP's
  testFixtures source set.
- **`TestPersistenceModule`** — `app/src/testFixtures/java/de/lemke/sudoku/TestPersistenceModule.kt`
  — replaces the production Room module with an in-memory database for the same reason.
- **`TestFixturesModuleInstallationTest`** exists on both sides
  (`app/src/test/java/de/lemke/sudoku/` and `app/src/androidTest/java/de/lemke/sudoku/`) as a
  permanent regression guard: each asserts an injected settings write never lands in production
  `SharedPreferences`, so if Hilt's KSP aggregation ever silently drops these modules for either
  consumer, that test turns red instead of failing silently.
