# History Tab, Nutrient Enrichment, Meal Management & Dashboard Integration
## Specification Document

---

## Problem Statement

The current fitness meal tracking application provides meal logging via the Meal tab and a daily overview via the Dashboard tab. However, users lack:

1. **Historical meal visibility**: No way to see individual meals logged on previous days, limit browsing history
2. **Rich nutrient tracking**: Only basic macronutrients (kcal, carbs, fat, protein); missing saturated fat, sugar, minerals, vitamins which are critical for comprehensive dietary analysis
3. **Meal management from history**: Cannot edit or delete meals from a history view; must return to logging tab
4. **Intuitive navigation**: No gesture-based (swipe) navigation between days; current buttons are non-standard
5. **Dashboard-to-detail navigation**: Macronutrient card on dashboard is non-interactive; tapping doesn't provide deeper insight

---

## Goals

- Add a **History tab** with swipe-based day navigation (right = previous day, left = next day) showing daily meal entries and totals
- Enrich **nutrient tracking** to include saturated fat, sugar, minerals, and vitamins in addition to existing macronutrients
- Enable **meal editing and deletion** directly from the History tab UI
- Provide **value override capability** via a dedicated button interface (not inline editing)
- Integrate **dashboard macronutrient card** with tap-to-navigate to History tab for the current day
- Maintain **Material 3 design**, **Italian UI text**, and **backward compatibility** with existing data persistence

---

## Non-Goals

- Redesign existing Meal logging or Settings tabs
- Change authentication or data persistence architecture (Room database remains primary)
- Modify onboarding flow
- Create new nutrient computation algorithms (consume existing API responses; use OFF catalog data as baseline)
- Bulk import/export of meals or meal templates from History
- Social sharing or comparison features

---

## User Stories

1. **As a user**, I want to navigate through my meal history by swiping left/right on the History tab, so that I can quickly review meals from different days without clicking buttons.

2. **As a user**, I want to see a comprehensive list of all meals I logged on a selected day, including portion size, unit, and both basic and advanced nutrients (saturated fat, sugar, minerals, vitamins), so that I can track my detailed dietary intake.

3. **As a user**, I want to edit a meal I logged on a previous day directly from the History tab, so that I can fix mistakes without relogging the meal.

4. **As a user**, I want to delete a meal I logged on a previous day from the History tab, so that I can remove erroneous entries and correct my daily totals.

5. **As a user**, I want to add a new meal from the History tab for the currently selected day, so that I can log meals without switching tabs.

6. **As a user**, I want to override nutrient values for a meal via a dedicated button (not inline editing), so that I can correct inaccurate database values with intention and clarity.

7. **As a user**, I want to tap the macronutrient card on the Dashboard, so that I can see the detailed breakdown of my meals for today without manually navigating to History.

8. **As a user**, I want nutrient data (including new fields like saturated fat, sugar, minerals, vitamins) to persist across app restarts, so that my comprehensive nutritional records remain stable.

---

## Functional Requirements

### F1: History Tab Navigation & UI
- History tab must be visible in the bottom navigation bar (existing navigation structure retained)
- Day navigation via **horizontal swipe gestures**:
  - Swipe right → show previous day (if available)
  - Swipe left → show next day (if available)
- Display **current day indicator** (date, day of week in Italian)
- Show **daily totals** prominently: kcal intake, carbs, fat, protein, saturated fat, sugar (grams), minerals, vitamins
- Show **individual meal list** below totals with:
  - Meal type (Breakfast/Lunch/Dinner/Snacks in Italian)
  - Food name and brand (if available)
  - Portion size (quantity + unit, e.g., "150g", "2 medium")
  - Nutrients per portion: kcal, protein, carbs, fat, saturated fat, sugar, minerals, vitamins
- Support **scrolling within the daily view** if meals/nutrients exceed viewport
- Render at least **90 days of history** accessible by swipe navigation

### F2: Meal Entry Management from History
- **Edit meal**: Tap meal entry → open Meal Logging screen prepopulated with existing values (existing flow reused)
- **Delete meal**: Swipe menu on meal entry or delete button → confirmation dialog → remove from database and refresh daily totals
- **Add meal**: "Add meal" button in History tab → open Meal Logging screen for selected date (pre-select the date context)
- All operations update **daily totals in real-time** after save/delete
- Confirmation dialogs use existing Material 3 AlertDialog pattern

### F3: Nutrient Enrichment
- **Extended nutrient tracking**:
  - Saturated fat (g per 100g, cumulative)
  - Sugar (g per 100g, cumulative)
  - Minerals: Iron, Calcium, Magnesium, Zinc (mg per 100g, cumulative)  
  - Vitamins: Vitamin C, Vitamin D, Vitamin B12 (mcg or mg per 100g, cumulative)
- Store in **FoodItemEntity** new columns (via DB migration):
  - `saturated_fat_100`, `sugar_100` (Double)
  - `iron_100`, `calcium_100`, `magnesium_100`, `zinc_100` (Double)
  - `vitamin_c_100`, `vitamin_d_100`, `vitamin_b12_100` (Double)
- Store in **MealEntryEntity** new columns (cumulative for the logged portion):
  - `saturated_fat_total`, `sugar_total` (Double)
  - `iron_total`, `calcium_total`, `magnesium_total`, `zinc_total` (Double)
  - `vitamin_c_total`, `vitamin_d_total`, `vitamin_b12_total` (Double)
- **NutrientResolverService** extended to compute all nutrients from FoodItemEntity or override
- Populate additional nutrients from **OFF (Open Food Facts) API responses** when available; null/zero when unavailable

### F4: Value Override (Button-Based)
- **Override button** positioned separately on meal card (not inline with values)
- Button design: Material 3 TextButton or OutlinedButton, labeled "Modifica valori nutrizionali" (Edit nutrient values)
- Tap button → dialog with editable fields for all 12 nutrients (4 base + 8 enriched)
- Save override → update NutritionOverrideEntity and refresh daily totals
- Override persistence: stored in **NutritionOverrideEntity** with expandable schema for new nutrients

### F5: Dashboard Macronutrient Card Integration
- Existing macronutrient card (kcal, carbs, fat, protein percentages) becomes **clickable** (Material 3 ripple/elevation feedback)
- Tap card → navigate to History tab, auto-select current date (today)
- Maintain existing card appearance and metrics; only add interaction

### F6: Data Persistence & Backward Compatibility
- **DB migration**: Add new columns to `food_item` and `meal_entry` tables
- Existing entries: new columns default to NULL or 0.0
- Reads handle NULL gracefully (display as N/A or omit enriched section if all NULL)
- **Queries**: Update DAO methods to include new columns in SELECT and aggregation
- No breaking changes to existing data formats or export schemas

---

## Non-Functional Requirements

### NF1: Performance
- Swipe navigation response time < 300 ms (must feel smooth in Compose)
- Load 90-day history with < 500 ms latency (optimize DAO queries with indexes)
- Meal deletion/edit operations complete within < 1 s (async via viewModelScope)

### NF2: Usability
- Swipe gesture threshold: standard Android gesture detector (velocity + distance)
- Fallback buttons available for accessibility (e.g., physical keyboard, screen readers)
- Material 3 color scheme for all new UI components
- Italian text throughout (use existing `strings-it.xml` naming convention)

### NF3: Reliability
- Confirm all DB migrations execute without data loss
- Handle edge cases: empty history, single day only, null nutrient values
- Graceful error states: network failure (if OFF API integration added later), DB access errors

### NF4: Maintainability
- Preserve existing Jetpack Compose structure and ViewModel pattern
- Extend existing use cases (ObserveDashboardUseCase, ObserveHistoryUseCase) rather than creating parallel flows
- Keep NutrientResolverService responsible for nutrient computation logic
- Add repository methods symmetrically (read/write for new nutrients)

---

## Edge Cases

1. **No meals logged**: History tab shows selected day with 0 kcal, all nutrients at 0, empty meal list, and message "Nessun pasto registrato" (No meals logged)

2. **Single day of history**: User with only today's data; swipe left/right boundary handled gracefully (button disabled or no-op)

3. **Null nutrient values**: OFF data incomplete; display:
   - Base nutrients (kcal, carbs, fat, protein): always available (fallback to user estimates)
   - Enriched nutrients: show "—" or omit section if entirely NULL/zero for the day

4. **Meal edited after override**: If user edits a meal (changes quantity), override values preserved for next logged portion; old totals recalculated

5. **Very large portions**: Nutrients scale linearly (no capping); display format handles values > 1000 without truncation (e.g., "2500 mg" for iron)

6. **Timezone boundary edge**: Date boundaries respect device timezone; logging at 23:59 on Nov 30 goes to Nov 30, not Dec 1

7. **Concurrent edits**: App single-threaded by design (Compose/ViewModelScope); no race conditions expected; if user edits meal while refresh in progress, latest write wins

8. **Large swipe velocity**: Rapid repeated swipes handled by debouncing in ViewModel state updates; prevents "flicker" or state corruption

---

## Assumptions

1. **OFF data available**: Nutrient enrichment assumes OFF API (or embedded JSON catalog) provides saturated fat, sugar, mineral, and vitamin data for foods; if unavailable, field remains NULL
2. **Single user app**: No multi-account; single local database per device
3. **Compose adoption complete**: Fragments/View-based code not used; all UI via Jetpack Compose
4. **Room database as persistence layer**: Local SQLite via Room DAO; no remote sync scope for this feature
5. **Linear scaling**: Nutrients scale proportionally to portion size; no non-linear effects (e.g., bioavailability)
6. **Material 3 library available**: androidx.compose.material3 already in project dependencies
7. **Existing gesture handling infrastructure**: GestureScope or similar already in Compose environment for detecting swipe
8. **Italian strings already present**: Existing `strings-it.xml` pattern used; new keys added following same convention
9. **Existing MealLoggingViewModel reusable**: Edit/add flows leverage existing meal logging infrastructure; no parallel edit flow created

---

## Definition of Done

A feature is considered complete when:

1. **Code implemented & tested locally**:
   - All 12 nutrients tracked and stored in FoodItemEntity and MealEntryEntity
   - HistoryRoute enhanced with swipe gesture handling, meal list, and edit/delete UI
   - NutrientResolverService extended to resolve all 12 nutrients
   - Database schema migrated with new columns
   - Meal editing/deletion/addition workflows functional from History tab

2. **Material 3 & Italian localization**:
   - All new UI components follow Material 3 design system
   - All new user-facing string keys present in `strings.xml` and `strings-it.xml`
   - No hardcoded English text in new code

3. **Dashboard integration**:
   - Macronutrient card clickable (ripple feedback, navigation to History + today's date)
   - Existing card appearance unchanged

4. **Data persistence & backward compatibility**:
   - DB migration scripts execute cleanly on existing databases
   - Null/zero handling verified for new nutrient fields
   - Manual testing confirms data persists across app restart

5. **Manual acceptance tests pass**:
   - All 12 acceptance criteria from `acceptance.json` verified manually (see below)

6. **No regressions**:
   - Existing Meal Logging, Dashboard, and Settings tabs remain functional
   - CI/CD green (if applicable)

---

## Risk Assessment

### Risk: Breaking Change (High)
- **Definition**: Database schema migration fails or data loss on old app version
- **Mitigation**: 
  - Thoroughly test migration on sample data before deployment
  - Provide backward-compatible read logic (NULL → default values)
  - Document migration steps for users

### Risk: Performance (Medium)
- **Definition**: Swipe navigation or 90-day query is slow on older devices
- **Mitigation**:
  - Optimize DAO queries with proper indexes (localDate, createdAt)
  - Lazy-load meals for selected day (don't fetch all 90 days upfront)
  - Profile performance on mid-range device (e.g., Pixel 3a)
  - Debounce swipe events to prevent excessive recomposition

### Risk: UI/UX Complexity (Low)
- **Definition**: Too many nutrients overwhelm user; History tab becomes cluttered
- **Mitigation**:
  - Group nutrients logically (macronutrients, minerals, vitamins)
  - Use collapsible sections if space limited
  - User feedback loop post-launch to adjust layout

---

## Acceptance Criteria (Linked to acceptance.json)

See `.agents-work/2026-04-13_history-nutrients-navigation/acceptance.json` for detailed machine-readable acceptance criteria.

**Summary**:
- AC-001: History tab visible in navigation bar
- AC-002 through AC-009: Swipe navigation, meal list display, edit/delete, add meal, override button
- AC-010: Dashboard macronutrient card tap navigates to History
- AC-011: Nutrient data persists across restart

---

