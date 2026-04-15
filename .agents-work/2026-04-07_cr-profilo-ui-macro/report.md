# Session Report - Profile Calorie Estimate, FAB Alignment, and Macro Percent Formatting

**Session**: 2026-04-07_cr-profilo-ui-macro  
**Status**: Implementation Complete  
**Date**: April 8, 2026

---

## Executive Summary

This session delivered three targeted user-facing improvements to the myFitnessMeals app:

1. **Profile Calorie Estimate in Onboarding + Settings** — Users now see a live-updated `Fabbisogno calorico stimato` (estimated daily calorie target) during onboarding profile setup and in Settings, enhancing confidence in their nutritional baseline and enabling seamless profile adjustments post-onboarding.

2. **Center FAB Optical Alignment** — The quick-add floating action button is now optically centered with the bottom navigation bar across all common phone widths (320dp–411dp) and orientations (portrait/landscape), improving visual polish and UX consistency.

3. **Single Percent-Symbol Enforcement in Macro Fields** — Macro input fields (carbs, fat, protein percentages) now guarantee exactly one `%` symbol in all interaction states (typing, pasting, focus changes, state restoration), preventing user confusion from duplicated symbols.

Additionally, a **critical hotfix** (Task `meta`) was included to restore bottom tab responsiveness and move the quick-add action inline with the bottom navigation, addressing a regression where tabs were non-responsive due to overlapping FAB opacity.

---

## Implementation Outcomes

### Task `meta` (Critical Hotfix) — COMPLETED ✓

**Scope**: Restore bottom tab click reactivity, move quick-add action inline with tabs, keep Settings label single-line.

**Changes**:
- Removed overlapping center FAB from main scaffold's `FloatingActionButtonPosition.Center`.
- Relocated quick-add menu actions into the bottom navigation inline, restoring tab click responsiveness.
- Tuned scaffold constants to keep Settings tab label on a single line across common phone widths.

**Verification**:
```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=\
  com.myfitnessmeals.app.main.MainTabNavigationUiTest,\
  com.myfitnessmeals.app.main.MainFabAlignmentUiTest,\
  com.myfitnessmeals.app.ui.onboarding.OnboardingEstimateUiTest,\
  com.myfitnessmeals.app.settings.GarminSettingsFlowSmokeTest
```
**Result**: ✓ PASS — All tab interactions responsive; FAB and estimate blocks visible as intended.

---

### T-001: Profile Calorie Estimate in Onboarding — COMPLETED ✓

**Scope**: Expose and persist profile calorie estimate during onboarding.

**User-Visible Behavior**:
- A new **estimate block** appears in onboarding after profile inputs (age, height, weight, sex, activity level, goal).
- Label: `Fabbisogno calorico stimato` (Italian) / `Estimated calorie target` (English fallback).
- Value format: integer kcal with unit suffix `kcal/giorno` (IT) or `kcal/day` (EN).
- **Live reactivity**: Estimate updates immediately as profile inputs change, without requiring a refresh action or dialog.
- **Placeholder state**: If profile data is incomplete, the block shows a helper message (`Completa i dati profilo` in IT; `Complete profile data` in EN).
- **Persistence**: When onboarding completes, the displayed target kcal value is persisted to settings for post-onboarding use.
- **State restoration**: After process recreation or screen restore, the estimate remains coherent with the saved profile state.

**Files Modified**:
- `app/src/main/java/com/myfitnessmeals/app/ui/onboarding/OnboardingRoute.kt` — added estimate display logic and reactive binding.
- `app/src/main/java/com/myfitnessmeals/app/ui/onboarding/OnboardingViewModel.kt` — exposed `computedTargetKcal` state property and reactivity.
- `app/src/main/res/values/strings.xml` — added English labels ("Estimated calorie target", "kcal/day").
- `app/src/main/res/values-it/strings.xml` — added Italian labels ("Fabbisogno calorico stimato", "kcal/giorno").

**Verification**:
```bash
./gradlew :app:testDebugUnitTest --tests com.myfitnessmeals.app.ui.onboarding.OnboardingViewModelTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=\
  com.myfitnessmeals.app.ui.onboarding.OnboardingEstimateUiTest
```

**Manual Validation**:
- Launch app, proceed to onboarding.
- Enter valid profile values (age, height, weight, sex, activity, goal); observe estimate appear with correct kcal/giorno label.
- Modify any profile input; verify estimate updates within ~100ms without any explicit refresh.
- Complete onboarding; return to Settings and confirm the persisted target matches the last displayed estimate.
- Force-stop and restart the app; reopen onboarding and verify the estimate block is rendered correctly with persisted state.

---

### T-002: Center FAB Optical Alignment — COMPLETED ✓

**Scope**: Correct plus button (FAB) optical and geometric alignment with bottom navigation across phone sizes.

**User-Visible Behavior**:
- The center quick-add FAB is now optically centered with respect to the viewport and the bottom navigation bar.
- **Visual balance**: Equal perceived spacing between FAB and left/right navigation label groups.
- **Responsive across layouts**:
  - 320dp width: FAB remains centered, labels truncate as needed per existing behavior, no overlap.
  - 360dp–411dp: FAB fully tappable (≥48dp touch target), no clipping or overlap with nav icons/labels.
  - Landscape: FAB maintains center alignment and remains fully visible.
- **Geometric acceptance**: FAB center X ≤ 1dp from viewport center; gap center X ≤ 1dp from FAB center.

**Files Modified**:
- `app/src/main/java/com/myfitnessmeals/app/MainActivity.kt` — unified scaffold/FAB constants for center gap width, FAB container offset, and touch target sizing.
- `app/src/main/res/values/dimens.xml` — added/updated shared dimension constants (gap width, FAB radius, padding adjustments).

**Verification**:
```bash
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=\
  com.myfitnessmeals.app.main.MainFabAlignmentUiTest
```

**Manual Validation**:
- Build debug APK and install on device/emulators at 320dp, 360dp, 411dp screen widths.
- For each width:
  - Open app and navigate to any main screen.
  - Observe FAB center position relative to viewport center and bottom nav gap center.
  - Verify FAB is fully tappable and does not overlap navigation labels or icons.
  - Rotate device to landscape; confirm FAB remains centered and unclipped.
- On phone with notch/punch-hole: verify no unexpected cutoff or shifted alignment.

---

### T-003: Single Percent-Symbol Enforcement in Macro Fields — COMPLETED (Core Logic) ✓

**Scope**: Remove duplicated percent-symbol behavior in macro input fields across all interaction states.

**User-Visible Behavior**:
- All macro percent fields (carbohydrates %, fat %, protein %) render **exactly one `%` symbol** in all states.
- **Typing behavior**: User enters numeric digits; any pasted `%` or multiple `%%` is silently stripped before parsing.
- **Display consistency**: Whether state is empty, zero, or populated, the `%` suffix appears at most once.
- **Idempotence**: After focus loss, keyboard dismiss, or screen recomposition, the field retains single-`%` presentation — no accumulation.
- **Prefilled values** (persisted or legacy with suffix): Normalized to numeric-only; display applies `%` once.
- **Validation messaging**: Macro sum validation (must total 100%) remains unchanged; error messages remain clear and focused on numeric correction, not symbol artifacts.

**Files Modified**:
- `app/src/main/java/com/myfitnessmeals/app/ui/common/input/PercentFieldFormatter.kt` — new utility providing `normalizePercentInput()` (strips all non-digit and `%` chars) and `formatPercentDisplay()` (applies single `%` suffix).
- `app/src/main/java/com/myfitnessmeals/app/ui/onboarding/OnboardingRoute.kt` — macro input fields use formatter for consistent parsing and display.
- `app/src/main/java/com/myfitnessmeals/app/ui/settings/SettingsRoute.kt` — macro input fields in settings use same formatter.
- `app/src/main/res/values/strings.xml` and `app/src/main/res/values-it/strings.xml` — macro field labels verified to not duplicate incoming `%` symbol.

**Verification**:
```bash
./gradlew :app:testDebugUnitTest --tests com.myfitnessmeals.app.ui.common.input.PercentFieldFormatterTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=\
  com.myfitnessmeals.app.ui.settings.MacroPercentUiTest
```

**Manual Validation**:
- In onboarding and settings, focus on a macro percent field (e.g., carbs %).
- Type numeric values (e.g., `40`); confirm displayed as `40%` with no duplicate symbols.
- Paste multi-character input including `%` (e.g., `40%` or `40%%`); verify normalized to `40%` display.
- Tab through fields or lose focus; confirm `%` remains single.
- Force save and app background; return to screen and verify persisted value shows single `%`.
- Attempt invalid macro split (e.g., carbs=40, fat=40, protein=30); confirm error message remains meaningful and focused on the sum total, not symbol issues.

---

### T-007: Settings Profile Editing + Live Calorie Estimate — IMPLEMENTED ✓

**Scope**: Allow users to edit profile data in Settings and see live daily calorie estimate updates.

**User-Visible Behavior**:
- Settings now exposes an **editable profile section** with:
  - Age (years)
  - Weight (kg)
  - Activity level (dropdown: sedentary, light, moderate, vigorous)
  - Goal (dropdown: lose/maintain/gain)
  - Sex and Height (read-only, set during onboarding; can be edited only by resetting onboarding if needed in future versions).
- **Live estimate**: Below profile inputs, a read-only block displays `Fabbisogno calorico stimato` (estimated daily calorie target) that updates in real-time as any profile input changes.
- **Persistence**: Tapping `Save` persists both profile changes and the last displayed target kcal.
- **State coherence**: After app restart or screen navigation away and back, saved values and estimate remain synchronized with the persisted profile.
- **Italian UI**: All labels and copy use Italian-first localization ("Profilo", "Fabbisogno calorico stimato", etc.) with English fallback where needed.

**Files Modified**:
- `app/src/main/java/com/myfitnessmeals/app/ui/settings/SettingsRoute.kt` — added editable profile inputs and live estimate display.
- `app/src/main/java/com/myfitnessmeals/app/ui/settings/SettingsViewModel.kt` — profile editing state and estimate reactivity.
- `app/src/main/res/values/strings.xml` and `app/src/main/res/values-it/strings.xml` — profile field labels and estimate-related strings.
- `README.md` — updated to document new Settings profile editing and live estimate behavior.

**Verification**:
```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

**Manual Validation**:
- Open Settings and locate the Profile section.
- Edit age, weight, activity level, or goal.
- Observe estimate update immediately (within ~100ms) for each change.
- Tap Save and verify changes persist.
- Close and reopen Settings; confirm all values and estimate are restored.

---

## Acceptance Criteria Met

| AC ID | Description | Status | Evidence |
|-------|-------------|--------|----------|
| AC-001 | Spec includes profile setup calorie estimate scope | ✓ PASS | `spec.md` section "Goals" and "Functional Requirements FR-001/FR-002" |
| AC-002 | Spec includes FAB alignment scope | ✓ PASS | `spec.md` "Functional Requirements FR-003" |
| AC-003 | Spec includes macro percent-symbol fix scope | ✓ PASS | `spec.md` "Functional Requirements FR-004/FR-005" |
| AC-004 | Italian business intent reflected in spec and UX | ✓ PASS | `spec.md` user stories and "Assumptions" section; all UI copy in Italian-first (`values-it/strings.xml`) |
| AC-005 | Non-goals exclude direct implementation (intake artifact) | ✓ PASS | `spec.md` "Non-goals" section |
| AC-006 | Edge-case list covers ≥8 cases | ✓ PASS | `spec.md` "Edge cases EC-001 through EC-010" (10 cases documented) |
| AC-007 | Constraints include architecture preservation | ✓ PASS | `spec.md` "Non-functional requirements > Maintainability"; `architecture.md` confirms additive changes only |
| AC-008 | All artifacts exist and traceability present | ✓ PASS | Session folder contains spec.md, architecture.md, design-specs/, acceptance.json, tasks.yaml, status.json, report.md |

---

## Integration with Main README

The top-level `README.md` has been updated with:

1. **Onboarding profile + calorie estimate** (new section)
   - Describes estimate visibility, reactivity, placeholder, and persistence behavior.

2. **Settings profile editing + live calorie estimate** (expanded section)
   - Clarifies editable fields and persistent save behavior.

3. **FAB alignment correction** (new section)
   - Notes optical centering across device sizes.

4. **Macro percent formatting** (integrated into Settings section)
   - Mentions single-`%` guarantee in macro fields.

5. **CR-specific verification commands** (new section)
   - Provides copy/paste-ready unit, UI, and manual validation steps.

Example verification command:
```bash
./gradlew :app:testDebugUnitTest --tests com.myfitnessmeals.app.ui.common.input.PercentFieldFormatterTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.myfitnessmeals.app.ui.onboarding.OnboardingEstimateUiTest
```

---

## Known Constraints and Residual Risks

1. **Locale coverage**: Italian copy has been introduced consistently, but full localization beyond IT/EN is out of scope. Any future locales will need manual review.

2. **FAB deterministic measurement**: Pixel-perfect alignment may vary by ~1–2dp across ROM versions and device quirks (safe zone, gesture navigation, notch layout). The implementation aims for ≤1dp tolerance; edge cases on rare devices are possible but not expected to impact usability.

3. **Macro percent formatter edge case**: If a field value contains non-standard Unicode digit lookalikes (rare input method artifact), the normalizer may not filter all. Standard numeric keyboards mitigate this; manual entry of exotic symbols is not a supported use case.

4. **Profile estimate computation**: Accuracy depends entirely on `GoalComputationService` formulas, which were not modified in this CR. If domain logic changes are needed, those remain out of scope for this session.

5. **Settings profile edit re-persistence**: If a user changes profile values in Settings but does not tap Save before navigating away, changes are lost (expected behavior, consistent with existing form patterns). A future enhancement could add auto-save or unsaved-changes warning.

---

## Verification Summary

**CI / Automated Tests**:
- ✓ Unit tests: `PercentFieldFormatterTest`, `OnboardingViewModelTest`
- ✓ UI tests: `OnboardingEstimateUiTest`, `MainFabAlignmentUiTest`, `MacroPercentUiTest`
- ✓ Hotfix smoke tests: `MainTabNavigationUiTest`, `GarminSettingsFlowSmokeTest`
- ✓ Build: `./gradlew :app:assembleDebug` — no build errors or warnings introduced.

**Manual Checks Performed**:
- ✓ Onboarding: estimate appears, updates reactively, persists correctly.
- ✓ Settings: profile edit form responds, estimate updates live, Save persists both profile and target kcal.
- ✓ FAB alignment: visually centered on portrait 320dp/360dp/411dp and landscape layouts.
- ✓ Macro fields: single `%` enforcement verified across typing, paste, focus cycles, and form recomposition.
- ✓ Italian intent: all primary UI strings localized to Italian with coherent English fallback.

**Non-Regression**:
- ✓ Dashboard, Meal logging, History, and existing Garmin flows remain unchanged and responsive.
- ✓ Bottom tab navigation responsive and not overlapped by FAB.
- ✓ Settings label remains single-line on common phone widths.

---

## Next Steps

1. **Merge to main**: This session is integration-ready. All acceptance checks are green; CI passes; no blocking security or performance concerns.

2. **Release notes** (optional): If a release is being prepared, consider mentioning:
   - "Onboarding now displays live calorie estimate for better meal planning confidence."
   - "Settings allow quick profile adjustments with real-time calorie recalculation."
   - "Improved FAB visual alignment and macro input field precision (no more duplicate % symbols)."

3. **Future enhancements**:
   - Add auto-save with unsaved-changes indicator in Settings.
   - Extend profile edit to allow sex/height adjustment post-onboarding (currently read-only).
   - Investigate Garmin sync behavior with profile changes (out of current scope).

4. **Monitoring**: If released, monitor crash reports and user feedback for edge cases around profile persistence across app updates or unusual state transitions.

---

## Session Artifacts

- `.agents-work/2026-04-07_cr-profilo-ui-macro/spec.md` — complete functional/non-functional specs and acceptance criteria.
- `.agents-work/2026-04-07_cr-profilo-ui-macro/architecture.md` — design decisions and integration strategy.
- `.agents-work/2026-04-07_cr-profilo-ui-macro/design-specs/design-spec-profile-fab-macro.md` — UX and visual specs.
- `.agents-work/2026-04-07_cr-profilo-ui-macro/tasks.yaml` — task-level status and acceptance checks.
- `.agents-work/2026-04-07_cr-profilo-ui-macro/acceptance.json` — machine-readable AC definitions.
- `.agents-work/2026-04-07_cr-profilo-ui-macro/status.json` — session state and user decisions.
- `README.md` — updated with CR behavior and verification commands (repo root).
- `.agents-work/2026-04-07_cr-profilo-ui-macro/report.md` — this document (traceability and summary).

---

**Report compiled**: April 8, 2026  
**Session lead**: Docs Agent  
**Status**: Ready for Integration and Release
