# Architecture Design: History Tab, Nutrient Enrichment & Meal Management

## Overview

This document defines the layered architecture for the History tab feature including nutrient enrichment (12 nutrients: 4 base + 8 enriched), meal management (edit/delete/add), and dashboard integration. The design preserves the existing Jetpack Compose + Room + clean architecture pattern while introducing:

- **Data layer**: Extended `FoodItemEntity` and `MealEntryEntity` with 8 new nutrient columns + nullable defaults for backward compatibility
- **UI layer**: Enhanced `HistoryRoute` with swipe gesture navigation, meal card rendering, and edit dialogs
- **Domain layer**: Extended `HistoryDaySnapshot` and new `HistoryMealCard` models to include enriched nutrients
- **Gesture handling**: Compose-native horizontal swipe via `detectHorizontalDragGestures` (Material 3 GestureScope)
- **Override system**: Enhanced `NutritionOverrideEntity` to support all 12 nutrients with button-based access (not inline)
- **Dashboard integration**: Macronutrient card becomes clickable, routes to History for today
- **Backward compatibility**: All new columns nullable; migration scripted; existing data unaffected

---

## Module Architecture & Responsibilities

### Data Layer (`app/src/main/java/com/myfitnessmeals/app/data/`)

#### Entities (Local)

**FoodItemEntity** (extended)
- Existing: `kcal_100`, `carb_100`, `fat_100`, `protein_100`
- New: `saturated_fat_100`, `sugar_100`, `iron_100`, `calcium_100`, `magnesium_100`, `zinc_100`, `vitamin_c_100`, `vitamin_d_100`, `vitamin_b12_100` (all Double, nullable for OFF data compatibility)
- Storage: Per-100g (unit normalized), sourced from OFF API or user input
- Indexes: existing (barcode, name, canonicalExternalKey) + no new indexes needed

**MealEntryEntity** (extended)
- Existing: `kcal_total`, `carb_total`, `fat_total`, `protein_total`
- New: `saturated_fat_total`, `sugar_total`, `iron_total`, `calcium_total`, `magnesium_total`, `zinc_total`, `vitamin_c_total`, `vitamin_d_total`, `vitamin_b12_total` (all Double, nullable, computed from portion)
- Storage: Cumulative totals for logged portion (computed at save time from FoodItemEntity per-100g values)
- Indexes: no changes (localDate, foodId existing)

**NutritionOverrideEntity** (extended)
- Existing: `kcal_100`, `carb_100`, `fat_100`, `protein_100`
- New: same 8 enriched nutrients as FoodItemEntity, all nullable
- Scope: per FoodItem; one override per food (cascading delete on food deletion)
- Semantics: intent-driven, user-provided corrections to OFF baseline

**DailySummaryEntity** (extended)
- Existing: aggregations of base 4 macronutrients
- New: aggregations of all 8 enriched nutrients (sums of all meals for the day)
- Purpose: denormalized cache for fast History/Dashboard queries

#### DAOs (Query Layer)

**MealEntryDao** (extended)
```kotlin
// Existing
suspend fun getByDate(localDate: String): List<MealEntryEntity>  
suspend fun getTotalsForDate(localDate: String): MealEntryTotals

// New query returning enriched totals
suspend fun getTotalsForDateWithNutrients(localDate: String): MealEntryTotalsEnriched

// Aggregation for meal cards (returns MealEntryEntity with full detail)
suspend fun getMealsForDateGroupedByType(localDate: String): List<MealEntryEntity>
```

**DailySummaryDao** (extended)
```kotlin
// Existing
suspend fun getByDate(localDate: String): DailySummaryEntity?

// New range query
suspend fun getByDateRange(startDate: String, endDate: String): List<DailySummaryEntity>
```

#### Repositories (Transactional Business Logic)

**LocalDiaryRepository** (extended)
- Existing: `addMealEntry()`, `updateMealEntry()`, `deleteMealEntry()`, `setDailyTarget()`, `getDailySummary()`, `getDailySummariesInRange()`
- New public methods:
  - `getMealsForDateWithDetails(localDate: String): List<MealWithFoodAndOverride>` — returns meal entries with joined food and override data
  - `getDailyTotalsEnriched(localDate: String): DailyTotalsEnriched` — enriched aggregates (12 nutrients)
  - All existing transactional patterns preserved; new mutations automatically recalculate daily summary

**LocalOverrideRepository** (no schema change, but reused)
- Existing: override CRUD for 4 base nutrients
- Pattern: extends to support all 12 nutrients in NutritionOverrideEntity schema
- No code change needed; new columns are additive; save logic auto-includes nulls

#### Database Migration

**MIGRATION_2_3**
- Schema: Add 8 new nullable Double columns to `food_item`, `meal_entry`, `nutrition_override`, `daily_summary` tables
- Defaults: All NULL (backward compatible)
- Indexes: None (existing indexes untouched)
- Forward compatibility: no deletions; pure additive

---

### Domain Layer (`app/src/main/java/com/myfitnessmeals/app/domain/`)

#### Models (UseCase Inputs/Outputs)

**HistoryDaySnapshot** (extended)
```kotlin
data class HistoryDaySnapshot(
    val localDate: String,
    val kcalTarget: Double,
    val kcalIntake: Double,
    val kcalBurned: Double,
    val kcalRemaining: Double,
    val carbGrams: Double,
    val fatGrams: Double,
    val proteinGrams: Double,
    // New enriched totals
    val saturatedFatGrams: Double?,
    val sugarGrams: Double?,
    val ironMg: Double?,
    val calciumMg: Double?,
    val magnesiumMg: Double?,
    val zincMg: Double?,
    val vitaminCMg: Double?,
    val vitaminDMcg: Double?,
    val vitaminB12Mcg: Double?,
)
```

**HistoryMealCard** (new)
```kotlin
data class HistoryMealCard(
    val mealEntryId: Long,
    val foodName: String,
    val brand: String?,
    val mealType: MealType,
    val quantityValue: Double,
    val quantityUnit: String,
    // All 12 nutrients (per portion, from totals)
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val saturatedFat: Double?,
    val sugar: Double?,
    val iron: Double?,
    val calcium: Double?,
    val magnesium: Double?,
    val zinc: Double?,
    val vitaminC: Double?,
    val vitaminD: Double?,
    val vitaminB12: Double?,
    // Override metadata
    val isOverridden: Boolean,
    val overrideSource: String, // "OFF" or "Manual"
)
```

#### UseCases (Business Logic Orchestration)

**ObserveHistoryUseCase** (extended)
```kotlin
class ObserveHistoryUseCase(
    private val diaryRepository: LocalDiaryRepository,
    private val settingsRepository: UserSettingsRepository,
) {
    suspend operator fun invoke(days: Int = 90): List<HistoryDaySnapshot> {
        // Retrieve daily summaries for range with enriched totals
        // Return in reverse-chronological order (today first)
    }
}
```

**GetHistoryMealsForDayUseCase** (new)
```kotlin
class GetHistoryMealsForDayUseCase(
    private val diaryRepository: LocalDiaryRepository,
) {
    suspend operator fun invoke(localDate: String): List<HistoryMealCard> {
        // Fetch meals for date, join with food and override data
        // Map to HistoryMealCard DTOs
    }
}
```

**DeleteMealEntryUseCase** (existing, reused)
- Already handles transactional delete + summary recalculation
- No changes needed

**SaveMealEntryUseCase** (extended)
- Compute all 12 nutrient totals (not just 4 base)
- Save to MealEntryEntity extended columns
- Trigger daily summary refresh

#### Service: NutrientResolverService (extended)

Responsibility: Compute nutrient totals from FoodItemEntity per-100g values + override logic.

```kotlin
class NutrientResolverService {
    fun resolveNutrients(
        foodId: Long,
        quantityValue: Double,
        quantityUnit: String,
        foodItem: FoodItemEntity,
        overrideEntity: NutritionOverrideEntity?, // if exists
    ): NutrientResolution {
        // 1. Normalize quantity to grams
        // 2. Load per-100g from FoodItemEntity (base 4 + enriched 8)
        // 3. Apply override if exists (all 12 nutrients via NutritionOverrideEntity)
        // 4. Return all 12 totals for the portion
    }
}

data class NutrientResolution(
    val kcal: Double, val carbs: Double, val fat: Double, val protein: Double,
    val saturatedFat: Double?, val sugar: Double?,
    val iron: Double?, val calcium: Double?, val magnesium: Double?, val zinc: Double?,
    val vitaminC: Double?, val vitaminD: Double?, val vitaminB12: Double?,
    val sourcePerNutrient: Map<String, ResolvedSource>, // Which nutrient came from OFF vs Manual
)
```

---

### Presentation Layer (`app/src/main/java/com/myfitnessmeals/app/ui/`)

#### ViewModels

**HistoryViewModel** (enhanced)
```kotlin
data class HistoryUiState(
    val days: List<HistoryDaySnapshot> = emptyList(),
    val selectedIndex: Int = 0,
    val mealsForSelectedDay: List<HistoryMealCard> = emptyList(),
    val isSwipeInProgress: Boolean = false,
    val errorMessage: String? = null,
)

class HistoryViewModel(
    private val observeHistoryUseCase: ObserveHistoryUseCase,
    private val getHistoryMealsForDayUseCase: GetHistoryMealsForDayUseCase,
    private val deleteMealEntryUseCase: DeleteMealEntryUseCase,
) : ViewModel() {
    // New methods:
    suspend fun onSwipeLeft() // advance to next day (forward in time)
    suspend fun onSwipeRight() // go to previous day (backward in time)
    fun onEditMealTapped(mealEntryId: Long) // signal to parent to open MealLoggingScreen
    fun onDeleteMealTapped(mealEntryId: Long) // delete + refresh
    fun onOverrideMealTapped(mealEntryId: Long) // signal to parent dialog
}
```

**MealLoggingViewModel** (reused as-is)
- Already supports date pre-population via `selectedDate` state field
- Already handles edit/add/save with full meal entry
- History tab passes context (localDate) via navigation arg or ViewModel shared state
- No changes required

**DashboardViewModel** (extended)
```kotlin
class DashboardViewModel(...) {
    fun onMacroCardTapped() {
        // Navigate to History tab with today's date selected
        // Signal via event/callback to parent (MainActivity)
    }
}
```

#### Composables (UI Components)

**HistoryRoute** (enhanced)
- Entry point for Navigation
- Receives HistoryViewModel factory
- Renders HistoryScreen with all interactive callbacks
- Passes edit/delete/override actions to ViewModel

**HistoryScreen** (refactored)
```kotlin
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onSwipeGesture: (SwipeDirection) -> Unit,
    onEditMealTapped: (Long) -> Unit,
    onDeleteMealTapped: (Long) -> Unit,
    onAddMealTapped: () -> Unit,
    onOverrideMealTapped: (Long) -> Unit,
)
```

Hierarchy:
- Column (root, fillMaxSize)
  - Text: date selector (e.g., "9 apr 2026" or "Oggi" if today)
  - Card: daily totals (12 nutrients in 2-3 sections: macro, minerals, vitamins)
  - LazyColumn: meal list
    - MealCard per entry
    - MealCard expanded section: override button, edit, delete
  - Button: "Add meal"
- Swipe detection: GestureScope wrapping LazyColumn or Card

**MealCard** (new UI component)
```kotlin
@Composable
fun MealCard(
    meal: HistoryMealCard,
    onEditTapped: () -> Unit,
    onDeleteTapped: () -> Unit,
    onOverrideTapped: () -> Unit,
)
```

Visuals:
- Header: meal type icon + food name + brand (smaller text)
- Portion: "150 g" or "2 medium"
- Sections (collapsible/scrollable within card if needed):
  1. **Macros** (always visible): kcal, protein, carbs, fat with values
  2. **Saturated Fat & Sugar** (if not null): row layout
  3. **Minerals** (if not null): Ca, Fe, Mg, Zn in compact grid
  4. **Vitamins** (if not null): Vit C, Vit D, B12 in compact grid
- Button row: Edit | Delete | Override ("Modifica valori nutrizionali")

**DailyTotalsCard** (new)
```kotlin
@Composable
fun DailyTotalsCard(snapshot: HistoryDaySnapshot)
```

Visuals: mirrors layout of MealCard sections but aggregated for the day.

**MacronutrientCard** (Dashboard, modified)
- Make Container clickable (Material 3 ripple + elevation feedback)
- onClick: lambda passed from parent (navigate to History + set date to today)
- No visual change to card content or layout

**DeleteMealConfirmDialog** (new reusable)
```kotlin
@Composable
fun DeleteMealConfirmDialog(
    foodName: String,
    onConfirmDelete: () -> Unit,
    onCancel: () -> Unit,
)
```

Text: "Eliminare questo pasto?" + buttons "Annulla" / "Elimina"

**NutrientOverrideDialog** (new)
```kotlin
@Composable
fun NutrientOverrideDialog(
    meal: HistoryMealCard,
    onSaveOverride: (NutrientOverride) -> Unit,
    onCancel: () -> Unit,
)
```

Fields (12 input fields, grouped):
- Macros: kcal, protein, carbs, fat
- Enriched: saturated fat, sugar
- Minerals: iron, calcium, magnesium, zinc
- Vitamins: vitamin C, vitamin D, vitamin B12
All initially populated with current meal values. Optional null-handling (show as empty or "—").

#### Navigation Integration

**MainActivity** (modified)
- HistoryRoute added to NavHost
- MealLoggingRoute already exists
- New intent/callback: "edit meal from History" → pre-populate MealLoggingViewModel.selectedDate + foodId
- New intent/callback: "dashboard card tapped" → select History tab + set date to today

---

## Data Flow

### History Navigation & Display

```
HistoryViewModel.init()
  → ObserveHistoryUseCase.invoke(90)
    → LocalDiaryRepository.getDailySummariesInRange()
      → DailySummaryDao.getByDateRange() [includes aggregated enriched nutrients]
    → ObserveHistoryUseCase builds List<HistoryDaySnapshot> (reverse-chron)
  → HistoryUiState.days = snapshots, selectedIndex = 0 (today)

User swipes right:
  → HistoryViewModel.onSwipeRight()
    → selectedIndex++
    → trigger GetHistoryMealsForDayUseCase.invoke(days[selectedIndex].localDate)
      → LocalDiaryRepository.getMealsForDateWithDetails()
        → MealEntryDao.getMealsForDateGroupedByType() [joined with FoodItemEntity + NutritionOverrideEntity]
      → Result: List<HistoryMealCard>
  → HistoryUiState.mealsForSelectedDay = cards
  → HistoryScreen re-composes with new day
```

### Meal Edit Flow

```
User taps "Edit" on meal card:
  → HistoryViewModel.onEditMealTapped(mealEntryId)
    → Emit navigation event (parent handles)
  → MainActivity navigates to MealLoggingRoute with mealEntryId navArg
  → MealLoggingViewModel pre-loads existing meal + overrides
  → User edits → SaveMealEntryUseCase
    → NutrientResolverService computes all 12 nutrients
    → LocalDiaryRepository.updateMealEntry() [with enriched columns]
    → DailySummary recalculated
  → MealLoggingViewModel returns to History (pop backstack)
  → HistoryViewModel.refresh() or flow auto-updates
```

### Meal Delete Flow

```
User taps delete on meal card:
  → Show DeleteMealConfirmDialog
  → onConfirmDelete:
    → HistoryViewModel.onDeleteMealTapped(mealEntryId)
      → DeleteMealEntryUseCase.invoke(mealEntryId)
        → LocalDiaryRepository.deleteMealEntry()
          → MealEntryDao.deleteById()
          → DailySummaryDao recalculate
      → Refresh mealsForSelectedDay
  → UI re-renders; meal removed from list; daily totals updated
```

### Nutrient Override Flow

```
User taps "Modifica valori nutrizionali":
  → Show NutrientOverrideDialog (pre-filled with current values)
  → User edits 12 nutrient fields
  → onSaveOverride:
    → SaveNutritionOverrideUseCase.invoke(foodId, allNutrients)
      → LocalOverrideRepository.upsert(NutritionOverrideEntity with all 12 columns)
    → Trigger MealEntryEntity recompute for all meals with this food (for that date only? or all?)
    → Daily summary refresh
  → UI updates meal card with new values
```

### Dashboard-to-History Navigation

```
User taps MacronutrientCard on Dashboard:
  → DashboardViewModel.onMacroCardTapped()
    → Emit navigation event
  → MainActivity nav to HistoryRoute
  → HistoryViewModel init with special flag: preSelectDate = today
    → Set selectedIndex to position in days array where date == today
```

---

## Module Contracts & APIs

### MealRepository (LocalDiaryRepository) Contract

**Public Interface**

```kotlin
interface MealRepository {
    // Existing
    suspend fun addMealEntry(entry: NewMealEntry): Long
    suspend fun updateMealEntry(entryId: Long, entry: NewMealEntry): Boolean
    suspend fun deleteMealEntry(entryId: Long): Boolean
    suspend fun getDailySummary(localDate: String): DailySummaryEntity?
    suspend fun getDailySummariesInRange(start: String, end: String): List<DailySummaryEntity>

    // New
    suspend fun getMealsForDateWithDetails(localDate: String): List<MealWithFoodAndOverride>
    suspend fun getDailyTotalsEnriched(localDate: String): DailyTotalsEnriched?
}

data class MealWithFoodAndOverride(
    val entry: MealEntryEntity, // Full entity with enriched columns
    val food: FoodItemEntity,   // Per-100g data
    val override: NutritionOverrideEntity?, // User corrections or null
)

data class DailyTotalsEnriched(
    val kcal: Double, val carbs: Double, val fat: Double, val protein: Double,
    val saturatedFat: Double?, val sugar: Double?,
    val iron: Double?, val calcium: Double?, val magnesium: Double?, val zinc: Double?,
    val vitaminC: Double?, val vitaminD: Double?, val vitaminB12: Double?,
)
```

### NutrientResolverService Contract

```kotlin
interface NutrientResolverService {
    fun resolveNutrients(
        foodItem: FoodItemEntity,
        quantityValue: Double,
        quantityUnit: String,
        override: NutritionOverrideEntity?,
    ): NutrientResolution

    fun resolveFromSnapshot(
        snapshot: HistoryMealCard,
    ): NutrientResolution // Read-only; for UI display
}
```

### HistoryViewModel Contract

```kotlin
interface HistoryViewModelContract {
    val uiState: StateFlow<HistoryUiState>

    fun refresh()
    fun showPreviousDay()
    fun showNextDay()
    suspend fun deleteMeal(mealEntryId: Long)
    fun onEditMealTapped(mealEntryId: Long) // Emit nav event
    fun onOverrideMealTapped(mealEntryId: Long) // Emit nav event
}
```

### HistoryRoute Contract

```kotlin
@Composable
fun HistoryRoute(
    viewModel: HistoryViewModel,
    onNavigateToMealLogging: (mealEntryId: Long?, localDate: String) -> Unit,
    onNavigateToDashboard: () -> Unit,
)
```

### MealLoggingViewModel (Already Defined) — Extended Usage

```kotlin
// When navigating to edit meal from History:
mealLoggingViewModel.setSelectedDate(localDate) // Pre-set date
mealLoggingViewModel.loadMealForEditing(mealEntryId) // Pre-populate
```

---

## Error Handling Strategy

### Data Layer Errors
- **DB Constraints**: Foreign key violations → log + return false (not thrown)
- **Migration failures**: Logged; app may refuse to start (crash on intent if DB corrupt)
- **Query timeouts**: Logged; return null/empty; UI shows "Unable to load" message

### Domain Layer Errors
- **Validation errors** (e.g., negative quantity): Throw IllegalArgumentException; caller must handle
- **Missing entities** (meal not found on delete): Return false; caller logs or ignores
- **Aggregate errors** (e.g., can't compute macro percent): Return 0 or default value

### Presentation Layer Errors
- **ViewModel errors during load**: Catch in `viewModelScope.launch`; update `errorMessage` state
- **Navigation errors**: Log; silently skip (no UI feedback unless critical)
- **Override save errors**: Show SnackBar with "Errore nel salvataggio" + retry button

### User Feedback
- Delete confirmation dialog (prevents accidental loss)
- Toast/SnackBar for transient errors (network, computation)
- Static error text in screen for persistent issues (empty history, null nutrients)

---

## Configuration Strategy

### Feature Flags
- `ENABLE_NUTRIENT_ENRICHMENT`: boolean (default: true for launch, false for gradual rollout)
- `HISTORY_DAYS_AVAILABLE`: int (default: 90; can be increased/decreased)
- `SWIPE_GESTURE_ENABLED`: boolean (default: true; fallback to buttons if disabled)

### Constants
- Gesture velocity threshold: `SWIPE_VELOCITY_THRESHOLD = 400f` dp/s
- Gesture distance minimum: `SWIPE_DISTANCE_MIN = 50f` dp
- Null nutrient display: `"—"` or `"N/A"` (Italian: `"N/D"`)
- Portion unit display: Localized (grams → "g", medium → "medio", etc.)

### Strings (Italian)
```
history_title = "Storico"
history_date_label = "{date}" (e.g., "9 aprile 2026")
history_today = "Oggi"
history_totals_label = "Totali della giornata"
history_no_meals_label = "Nessun pasto registrato"
meal_type_breakfast = "Colazione"
meal_type_lunch = "Pranzo"
meal_type_dinner = "Cena"
meal_type_snacks = "Snack"
nutrient_kcal = "kcal"
nutrient_protein = "Proteine"
nutrient_carbs = "Carboidrati"
nutrient_fat = "Grassi"
nutrient_saturated_fat = "Grassi saturi"
nutrient_sugar = "Zuccheri"
nutrient_iron = "Ferro"
nutrient_calcium = "Calcio"
nutrient_magnesium = "Magnesio"
nutrient_zinc = "Zinco"
nutrient_vitamin_c = "Vitamina C"
nutrient_vitamin_d = "Vitamina D"
nutrient_vitamin_b12 = "Vitamina B12"
history_edit_meal = "Modifica"
history_delete_meal = "Elimina"
history_override_nutrients = "Modifica valori nutrizionali"
history_add_meal = "Aggiungi pasto"
history_delete_confirm = "Eliminare questo pasto?"
history_delete_confirm_button = "Elimina"
history_cancel_button = "Annulla"
dashboard_card_tap_hint = "Tocca per visualizzare i dettagli"
```

---

## Security Considerations

1. **Data Isolation**: All meal/override data tied to local user; no multi-account scope
2. **Input Validation**: All numeric inputs validated (positive, within reasonable bounds) before DB insert
3. **Override Audit**: NutritionOverrideEntity tracks `createdAt`/`updatedAt` for potential future audit log
4. **No Export in Scope**: Nutrient data stored locally in Room; no cloud sync or export in design (handled by Privacy/Export use cases separately)
5. **Gesture Abuse**: Rapid swipes debounced via ViewModel state update strategy; no race conditions

---

## Testing Strategy Overview

### Unit Tests (Domain Layer)
- **NutrientResolverService**: Compute totals from per-100g + override; verify linear scaling
- **ObserveHistoryUseCase**: Date range logic, reverse-chronological order, edge cases (single day, empty history)

### Integration Tests (Data Layer)
- **Migration MIGRATION_2_3**: Verify schema changes; existing data unaffected; new columns nullable
- **MealEntryDao**: Verify aggregation queries include enriched nutrients; NULL handling
- **LocalDiaryRepository**: Transactional consistency (meal delete triggers summary refresh)

### UI Tests (Presentation Layer)
- **HistoryRoute**: Swipe detection, day navigation, meal list rendering
- **MealCard**: Nutrient display, null value handling ("—"), edit/delete button taps
- **DeleteMealConfirmDialog**: Cancel/Confirm flows
- **Dashboard card**: Clickable state, navigation trigger

### Performance Tests
- 90-day history load: < 500 ms (benchmark on Pixel 5 or equivalent)
- Swipe gesture latency: < 300 ms (frame rate maintained)

---

## Directory Layout Proposal

```
app/
  src/main/
    java/com/myfitnessmeals/app/
      data/
        local/
          — FoodItemEntity.kt (extended)
          — MealEntryEntity.kt (extended)
          — NutritionOverrideEntity.kt (extended)
          — DailySummaryEntity.kt (extended)
          — MealEntryDao.kt (extended)
          — DailySummaryDao.kt (extended)
          — AppDatabase.kt (new migration MIGRATION_2_3)
        repository/
          — LocalDiaryRepository.kt (extended)
          — LocalOverrideRepository.kt (reused)
      domain/
        usecase/
          — ObserveHistoryUseCase.kt (extended)
          — GetHistoryMealsForDayUseCase.kt (new)
          — DeleteMealEntryUseCase.kt (reused)
          — SaveMealEntryUseCase.kt (extended)
        service/
          — NutrientResolverService.kt (extended)
        model/
          — HistoryDaySnapshot.kt (extended)
          — HistoryMealCard.kt (new)
          — DailyTotalsEnriched.kt (new)
      ui/
        history/
          — HistoryRoute.kt (enhanced)
          — HistoryViewModel.kt (enhanced)
          — HistoryScreen.kt (refactored for swipe)
          — MealCard.kt (new component)
          — DailyTotalsCard.kt (new component)
          — DeleteMealConfirmDialog.kt (new)
          — NutrientOverrideDialog.kt (new)
        dashboard/
          — MacronutrientCard.kt (modified: clickable)
          — DashboardViewModel.kt (extended: onMacroCardTapped callback)
        meal/
          — MealLoggingViewModel.kt (reused; supports pre-filled date)
    res/
      values/
        — strings.xml (new keys added)
      values-it/
        — strings-it.xml (new keys added)
```

---

## Migration & Rollout Strategy

1. **Pre-launch Testing**: Exhaustive DB migration test on real device (emulator + physical)
2. **Staged Rollout**: Feature flag `ENABLE_NUTRIENT_ENRICHMENT` to 0% → 10% → 50% → 100% over 2 weeks
3. **Backward Compatibility**: New columns are nullable; existing app versions unaffected; migration runs transparently
4. **Fallback**: If migration fails, app logs error and continues with base 4 nutrients only (graceful degradation)
5. **Breaking Change Risk Mitigation**: No deletions; purely additive schema; DAO queries remain backward-compatible

---
