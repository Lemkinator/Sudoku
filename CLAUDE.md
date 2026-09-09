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
