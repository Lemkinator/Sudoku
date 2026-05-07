# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK (requires keystore)
./gradlew lint                   # Run Android Lint
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run instrumented tests (device required)
```

Debug builds append `.debug` to the app ID — can be installed alongside release.

## Architecture

Clean Architecture with three layers, no ViewModels — Activities/Fragments inject use cases directly via Hilt.

```
de.lemke.sudoku/
├── ui/           # Activities, Fragments, custom views, RecyclerView adapters
├── domain/       # Use cases + domain models (Sudoku, Field, Position, Difficulty)
└── data/         # Room DB + DataStore preferences, mappers, repositories
```

**Data flow:** UI → UseCase → Repository → Room/DataStore. Reactive updates via `Flow<>`. Background work via `withContext(Dispatchers.Default)`.

**Domain models:** `Sudoku` (4×4/9×9/16×16), `Field` (cell with solution/value/notes), `Position` (row/col/block), `Difficulty` (VERY_EASY…EXPERT). Game logic lives on the domain objects themselves (`move()`, `setHint()`, `errorLimitReached()`).

**Sudoku modes:** Normal (modeLevel = 0), Level (modeLevel > 0), Daily (modeLevel = -1).

## Key Patterns

**Hilt DI:** `@HiltAndroidApp` on `App`, `@AndroidEntryPoint` on Activities/Fragments. `PersistenceModule` provides singleton `AppDatabase` and DataStore.

**Use cases:** Single-responsibility, `@Inject` constructor. Return domain types or `Flow<>`. Named with action-verb field names (parent CLAUDE.md convention).

**Room:** Two entities (`SudokuDb`, `FieldDb`). Schema exported to `app/schemas/`. Bidirectional mappers in `data/database/`.

**Settings:** All user preferences stored via `UserSettingsRepository` (DataStore). Daily sudoku notifications scheduled via `AlarmReceiver`.

## Notable Dependencies

- `dev.oneuiproject:oneui-design` — Samsung OneUI UI components (GitHub Maven repo)
- `de.sfuhrm:sudoku` — Sudoku generation algorithm
- `com.google.dagger:hilt-android` — DI
- `androidx.room` — Persistence
- `com.google.android.gms:play-services-games-v2` — Play Games achievements/leaderboards
- `io.kjson:kjson` — JSON serialization for import/export
