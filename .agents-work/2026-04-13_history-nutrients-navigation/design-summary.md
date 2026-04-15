# Design Summary & Notes

## Key Decisions Made

### 1. **Data Layer: Per-100g + Per-Portion Storage** (ADR-001)
- **FoodItemEntity** extended with 8 new columns (per-100g): saturated_fat_100, sugar_100, iron_100, calcium_100, magnesium_100, zinc_100, vitamin_c_100, vitamin_d_100, vitamin_b12_100
- **MealEntryEntity** extended with matching 8 columns (per-portion totals, computed at save time)
- **NutritionOverrideEntity** extended with same 8 columns (per-food user corrections)
- **DailySummaryEntity** extended with aggregated totals (for fast History queries)
- All new columns nullable for backward compatibility with incomplete OFF data

### 2. **Gesture Handling: Compose Native** (ADR-002)
- Use `detectHorizontalDragGestures` from androidx.compose.foundation.gestures
- Detect both distance (min 50 dp) and velocity (nice-to-have 400 dp/s) for natural feel
- Fallback buttons always available for accessibility
- Debouncing via ViewModel state flag to prevent duplicate swipes

### 3. **Nutrient Override: Per-Food, Button-Based** (ADR-003)
- Single override per food (`NutritionOverrideEntity`), applies to all meals with that food
- Separate "Modifica valori nutrizionali" button below meal card (not inline)
- Button opens modal dialog with all 12 nutrient fields editable
- Override creates audit trail (createdAt, updatedAt)

### 4. **Migration: Nullable Columns** (ADR-004)
- Implement `MIGRATION_2_3` using Room's `ALTER TABLE ADD COLUMN` pattern
- All new columns default to NULL (no conversion; purely additive)
- Signaling: NULL = no data available; 0.0 = measured value is zero
- Downgrade incompatible (old app can't read schema version 3; expected behavior)

### 5. **Dashboard-to-History Navigation: Composition Event** (ADR-005)
- Macronutrient card clickable (Material 3 ripple + elevation)
- Emit callback from DashboardViewModel; parent (MainActivity) handles routing
- Pre-set HistoryViewModel to today's date (invoked from listener)
- Clean separation: Dashboard doesn't know about History

---

## Module Contracts Summary

### **MealRepository** (LocalDiaryRepository)
```kotlin
Public API:
- addMealEntry(NewMealEntry): Long
- updateMealEntry(Long, NewMealEntry): Boolean
- deleteMealEntry(Long): Boolean
- getDailySummary(String): DailySummaryEntity?
- getDailySummariesInRange(String, String): List<DailySummaryEntity>
- getMealsForDateWithDetails(String): List<MealWithFoodAndOverride>  [NEW]
- getDailyTotalsEnriched(String): DailyTotalsEnriched?  [NEW]

Invariants:
- All mutations transactional
- Daily summary auto-recalculated post-mutation
- Nullable handling: NULL nutrients handled gracefully in aggregation
```

### **HistoryViewModel**
```kotlin
State:
- days: List<HistoryDaySnapshot>  [includes enriched totals]
- selectedIndex: Int
- mealsForSelectedDay: List<HistoryMealCard>  [NEW]
- isSwipeInProgress: Boolean  [for debouncing]
- errorMessage: String?

Actions:
- showPreviousDay()
- showNextDay()
- onEditMealTapped(Long)  [emit nav event]
- onDeleteMealTapped(Long)  [delete + refresh]
- onOverrideMealTapped(Long)  [emit override dialog event]
- refresh()  [reload all 90 days]

Contracts:
- Init: pre-populate 90-day window on creation
- Swipe handling: 300 ms debounce to prevent duplicate navigation
- Error state: show "Nessun pasto registrato" if no meals on selected day
```

### **HistoryRoute**
```kotlin
Inputs:
- viewModel: HistoryViewModel
- onNavigateToMealLogging: (mealEntryId: Long?, localDate: String) -> Unit
- onNavigateToDashboard: () -> Unit

Outputs:
- HistoryScreen composition with all UI interactions
- Emits nav events for edit/delete/add/override flows

Gesture Handling:
- Swipe left/right detected and passed to ViewModel
- Fallback buttons always visible
```

### **MealLoggingViewModel** (reused)
```kotlin
Extension for History context:
- setSelectedDate(String)  [pre-populate date field]
- loadMealForEditing(Long)  [pre-populate from meal ID]

Existing API (unchanged):
- saveMealEntry(): Boolean
- deleteMealEntry(Long): Boolean
- updateOverride(...): Boolean
```

---

## Tradeoffs & Risks

### ✅ **Minimal Complexity**
- Reused existing patterns (Room DAOs, ViewModel StateFlow, UseCase pattern)
- No new architectural layers; purely additive columns + new UI
- MealLoggingViewModel reuse avoids parallel edit flow

### ⚠️ **Denormalization & Consistency**
- **Risk**: Stored meal totals (MealEntryEntity) become stale if food data later corrected
- **Mitigation**: Document as by-design; meals immutable by date; corrections apply only to new meals
- **Mitigation**: User can re-edit meal to refresh from current food data

### ⚠️ **Performance**
- **Risk**: 90-day History query + rendering large meal lists
- **Mitigation**: Pre-computed aggregates in DailySummaryEntity (fast queries)
- **Mitigation**: LazyColumn renders only visible meal cards
- **Test**: Benchmark on mid-range device (Pixel 3a or equivalent); target < 500 ms load

### ⚠️ **Gesture UX**
- **Risk**: Swipe sensitivity too high (accidental triggering) or too low (unresponsive)
- **Mitigation**: Configurable thresholds (distance 50 dp, velocity 400 dp/s); adjust based on beta feedback
- **Mitigation**: Fallback buttons always accessible; swipe not mandatory

### ⚠️ **Migration Safety**
- **Risk**: New app version releases with migration; old app can't downgrade
- **Mitigation**: Schema version enforcement; version bump required on release
- **Mitigation**: Nullable columns; old app ignores new columns (no conflict)

### ✅ **Backward Compatibility**
- All changes additive; no deletions
- Null handling in code (required for null-safe Kotlin)
- Old meals remain queryable (no retroactive recomputation)

---

## Implementation Priorities

### Phase 1 (Foundation)
1. Implement `MIGRATION_2_3` + extended entity columns
2. Update DAOs with new aggregation queries
3. Extend `NutrientResolverService` for all 12 nutrients
4. Test migration on real device

### Phase 2 (History UI)
5. Refactor `HistoryRoute` + `HistoryViewModel` for new state
6. Implement swipe gesture detection + fallback buttons
7. Create `MealCard`, `DailyTotalsCard` components
8. UI test swipe navigation

### Phase 3 (Meal Management)
9. Integrate edit/delete flows (reuse `MealLoggingViewModel`)
10. Create `DeleteMealConfirmDialog`, `NutrientOverrideDialog`
11. Test delete + daily summary refresh

### Phase 4 (Dashboard Integration)
12. Make macronutrient card clickable
13. Implement navigation callback to History + pre-select today
14. Test Dashboard-to-History flow

### Phase 5 (Polish & Testing)
15. Italian localization (strings-it.xml)
16. Accessibility audit (keyboard nav, screen reader)
17. Performance profiling (90-day load, swipe latency)
18. Integration tests across all flows

---

## Open Questions & Future Work

- **OFF API Integration**: When/how to populate initial 8 enriched nutrients for existing foods? (Not in scope; may be separate task)
- **Mineral/Vitamin Units**: Are all nutrients in mg or mcg? Spec mentions mixed units; standardize unit handling in UI
- **Collapsible Sections**: If History card space constrained, collapse Minerals/Vitamins sections; expand on tap?
- **Swipe Velocity**: Adjust 400 dp/s threshold based on beta user feedback
- **Retention Policy**: Should History support > 90 days? Currently hard-coded; could make configurable
- **Concurrent Meal Edits**: If user edits meal while another in-flight, last write wins (acceptable for single-user app)
- **Export with Nutrients**: If export feature added later, include all 12 nutrients in CSV/JSON payload

---

## Testing Checklist (for QA/Reviewer phases)

### Data Layer
- [ ] Migration runs correctly on schema v2 → v3 DB
- [ ] Old meal data intact post-migration; new columns NULL
- [ ] New meals post-migration: enriched columns populated (non-NULL if OFF data available)
- [ ] Query aggregation: SUM(COALESCE(...)) handles NULL correctly
- [ ] Override upsert: all 12 nutrients saved & retrieved

### Domain Layer
- [ ] NutrientResolverService: linear scaling (2x portion = 2x nutrients)
- [ ] Null handling: missing OFF data → "—" display (not crash)
- [ ] Override precedence: user override > OFF baseline

### UI Layer
- [ ] HistoryRoute renders without crashing on empty history
- [ ] Swipe left/right navigation works on real device
- [ ] Fallback buttons functional (keyboard, accessibility)
- [ ] MealCard displays all 12 nutrients (or "—" if NULL)
- [ ] Delete confirmation dialog cancellable + deletable
- [ ] Override dialog: 12 fields editable; save updates meal card immediately
- [ ] Edit meal: reopens MealLoggingViewModel with pre-filled data

### Integration
- [ ] Dashboard macronutrient card tappable; navigates to History
- [ ] History pre-selected to today (not yesterday)
- [ ] Add meal from History: date pre-set correctly
- [ ] Italian localization: all new strings present in strings-it.xml

---
