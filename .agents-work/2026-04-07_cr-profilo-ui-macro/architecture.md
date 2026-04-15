# Architecture - Profilo Calorie Estimate + UI/Macro Fixes

## Overview
This change set should remain inside the existing single-module Android architecture (Compose UI + ViewModel + domain services + local repository) and deliver three targeted corrections: make calorie estimate behavior explicit during profile setup, stabilize plus button visual alignment in the main scaffold, and enforce single-symbol percent presentation for macro inputs. The design favors additive, low-risk formatting and UI wiring changes without altering persistence schema, navigation structure, or core domain formulas.

## Modules and components
- `ui/onboarding` (`OnboardingRoute.kt`): Profile setup form state, validation trigger, and visible calorie estimate display (`computedTargetKcal`) in the onboarding context.
- `domain/service` (`GoalComputationService.kt`): Existing calorie estimation and macro-split validation logic; remains the single source for target kcal computation and 100% validation.
- `data/repository` (`UserSettingsRepository`, `LocalUserSettingsRepository`): Stores canonical numeric macro percentages and target kcal; no display symbols persisted.
- `MainActivity` scaffold shell: Owns bottom navigation + center FAB layout where plus button alignment is corrected.
- `ui/settings` (`SettingsRoute.kt`): Macro percent editable fields for post-onboarding adjustments; should use a unified display/input formatting contract to prevent duplicate `%` behavior.
- `res/values*` string resources: Labels and locale copy (including Italian intent) for profile and macro fields.

## Data flow
- Profile input to estimate:
  - User edits age/height/weight/sex/activity/goal in onboarding state.
  - ViewModel maps state into `GoalProfileInput` and calls `GoalComputationService.computeTargetKcal`.
  - Result is written to `computedTargetKcal` and rendered in onboarding as immediate/explicit estimate feedback.
  - On completion, same numeric target is persisted through `UserSettingsRepository.saveSettings`.
- Macro percent input normalization:
  - User enters macro values in onboarding/settings fields.
  - UI formatter strips visual `%` artifacts before parse/save and re-applies exactly one visual suffix only at presentation boundary.
  - ViewModel validates numeric split via `validateMacroSplit` and persists numeric ints only.
- Plus button alignment:
  - `MainActivity` `Scaffold` remains owner of center FAB.
  - Bottom bar center gap and FAB container offset/size are tuned together in one place to avoid per-screen drift.

## Interfaces and contracts
- Domain contract (unchanged, reused):
  - `GoalComputationService.computeTargetKcal(input: GoalProfileInput): Double`
  - `GoalComputationService.validateMacroSplit(carbPct: Int, fatPct: Int, proteinPct: Int): Boolean`
- UI formatting contract (new internal contract, no API break):
  - `normalizePercentInput(raw: String): String` -> returns digits-only canonical string (or empty).
  - `formatPercentDisplay(value: String): String` -> returns either empty or `"<value>%"` once.
  - Rule: parsing/storage always uses normalized numeric text; display decorations are non-persistent.
- ViewModel/UI contract adjustments:
  - Onboarding and Settings ViewModels continue exposing `*PctInput` as strings, but consumers must treat them as canonical numeric strings.
  - UI layer applies suffix rendering strategy consistently so rebinding/focus changes cannot duplicate `%`.
- Resource contract:
  - Field labels in `strings.xml`/`values-it/strings.xml` should not force duplication when UI also renders suffix. One source of `%` truth only.

## Directory layout proposal
No structural refactor required. Keep current layout and add at most one shared UI utility for percent formatting.

- `app/src/main/java/com/myfitnessmeals/app/ui/common/input/PercentFieldFormatter.kt` (optional new helper)
- `app/src/main/java/com/myfitnessmeals/app/ui/onboarding/OnboardingRoute.kt` (consume helper + explicit estimate visibility behavior)
- `app/src/main/java/com/myfitnessmeals/app/ui/settings/SettingsRoute.kt` (consume helper)
- `app/src/main/java/com/myfitnessmeals/app/MainActivity.kt` (FAB/nav alignment tuning)
- `app/src/main/res/values/strings.xml` and `app/src/main/res/values-it/strings.xml` (percent label/source-of-truth cleanup)

## Error handling strategy
- Keep existing defensive parsing (`toIntOrNull`, `toDoubleOrNull`) and user-facing error messages.
- For percent fields, invalid tokens are normalized out before parse; parse failure still surfaces existing "Invalid numeric values" path.
- Preserve `IllegalArgumentException` handling from `GoalComputationService` in onboarding/settings flows.
- Avoid logging raw profile inputs in new code paths.

## Configuration strategy
- No new build config, feature flags, or persisted schema.
- Locale behavior remains resource-driven (`values` + `values-it`) with Italian intent maintained through copy updates only.
- UI behavior configuration is code-local (formatter utility + scaffold constants).

## Security considerations
- Continue storing only existing profile attributes; no new sensitive fields introduced.
- Keep percent formatting/display logic purely client-side and non-logging.
- Ensure analytics/observability integrations are not extended with raw personal metrics as part of this CR.

## Testing strategy overview
- Unit-level (future implementation phase):
  - Percent formatter normalization/display idempotence (including prefilled `%` values).
  - Goal computation invocation path for onboarding estimate updates with valid/invalid input.
- UI-level:
  - Onboarding shows target estimate coherently after valid profile entries and after state restore.
  - Settings/onboarding percent fields never show duplicated `%` after typing, focus changes, and recomposition.
  - FAB remains centered and visually aligned across common screen widths/densities.
- Regression scope:
  - Verify no behavior change outside onboarding/settings/FAB shell.

## Minimal integration plan
- Step 1: Introduce shared percent formatter utility and wire to onboarding/settings field rendering/parsing.
- Step 2: Align FAB with bottom navigation center slot by consolidating spacing constants in `MainActivity`.
- Step 3: Standardize percent-label source between UI and resources to prevent duplicate symbol production.
- Step 4: Validate profile estimate visibility and state restoration path in onboarding without changing domain formulas.
- Step 5: Run focused UI/manual checks from acceptance criteria only.
