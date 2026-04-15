# Design Spec - Profile Settings, FAB Alignment, Macro Percent UX

Session: 2026-04-07_cr-profilo-ui-macro
Task: meta
Scope: Profile setup calorie estimate presentation, center FAB alignment polish, single-percent behavior in macro inputs.

## 1) Existing Visual Language and Constraints
- UI stack: Android Jetpack Compose with Material 3 components.
- Existing patterns to preserve:
  - Full-screen `Surface` containers with `MaterialTheme.colorScheme.background`.
  - Vertical form layout in `Column` with 16dp horizontal padding and 8dp vertical spacing.
  - `OutlinedTextField` for numeric/settings inputs.
  - `Button` for primary action and `OutlinedButton` for secondary actions.
  - `testTag` usage for critical controls.
- No app-wide redesign. Keep typography, spacing rhythm, and component family consistent.

## 2) Information Architecture and Content Hierarchy
### 2.1 Onboarding (profile setup)
Top-to-bottom order (single scroll view if needed on small devices):
1. Screen title and one-line helper text.
2. Profile inputs: age, height, weight.
3. Goal/mode selectors (existing controls, unchanged behavior).
4. Macro split inputs (carb, fat, protein) with percent affordance.
5. Calorie estimate card/row (newly emphasized output in onboarding context).
6. Validation/error message region.
7. Primary CTA: complete onboarding.

Priority rule:
- Calorie estimate appears above the primary CTA so users can validate expected target before completion.

### 2.2 Settings screen
- Keep existing section order.
- Macro fields remain in settings nutrition block.
- Clarify percent semantics through single-symbol behavior and helper text only if space allows (do not add new complex components).

### 2.3 Main scaffold with center FAB
- Keep bottom navigation structure and center FAB pattern.
- FAB remains primary quick-add action with menu expansion.

## 3) Profile Calorie Estimate UX Specification
### 3.1 Presentation
- Show estimate as a distinct read-only value block in onboarding when profile numeric inputs are parseable and goal profile is valid.
- Label copy:
  - Italian intent: "Fabbisogno calorico stimato".
  - English fallback: "Estimated calorie target".
- Value format:
  - Rounded integer, unit suffix `kcal/giorno` (Italian) or `kcal/day` (fallback).
  - Example visual: `2350 kcal/giorno`.

### 3.2 Behavior states
- Empty/invalid profile inputs:
  - Estimate block displays placeholder ("Completa i dati profilo" / "Complete profile data").
  - No hard error until submit; maintain current non-intrusive behavior.
- Valid profile inputs:
  - Estimate updates reactively after input changes with no explicit refresh action.
- Submit success:
  - Persisted target must match last visible estimate.
- State restore/process recreation:
  - Last computed estimate should reappear consistently from state/persisted settings.

### 3.3 Visual treatment
- Use existing Material container style (no custom theme tokens required).
- Ensure estimate label uses supporting text style and value uses emphasized body/title style.
- Keep contrast at or above WCAG AA equivalent for text/background pairings in both light and dark themes.

## 4) Macro Percent Input UX (Single-% Rule)
### 4.1 Canonical interaction rule
- Input model is numeric-only.
- Presentation may include one percent sign, but never more than one.
- Rebinding, focus changes, IME actions, and recomposition must remain idempotent.

### 4.2 Field behavior
- While editing:
  - User can type digits.
  - If user pastes `40%` or `40%%`, field normalizes to `40` canonical value.
- Display state (non-editing or rendered label/assistive value):
  - Exactly one `%` indicator associated with the value.
- Labels:
  - Do not duplicate `%` in both label and value simultaneously if that results in visual `%%` confusion.
  - Preferred rule: keep label plain (e.g., "Carboidrati") and apply `%` only as suffix/visual adornment to the value context.

### 4.3 Validation and messaging
- Existing macro sum validation (must total 100) remains unchanged.
- Error state remains existing inline error style.
- Error copy should avoid mentioning symbol formatting; focus on numeric correction.

### 4.4 Edge-case behavior matrix
- Prefilled persisted value `40` -> shows one `%` affordance only.
- Prefilled persisted value `40%` (legacy/dirty source) -> normalized to `40`, then one `%` affordance.
- Empty -> empty display, no forced `%`.
- Zero -> `0` with one `%` affordance.
- Rapid tab/focus cycling -> no accumulation of `%`.

## 5) Center FAB Alignment Specification
### 5.1 Alignment intent
- FAB center axis must align with screen center axis and perceived center notch/gap of bottom navigation.
- Visual balance: equal apparent spacing between left and right nav item groups around the FAB gap.

### 5.2 Layout constraints
- Preserve `FabPosition.Center` and existing navigation items.
- Gap placeholder width and FAB diameter/offset must be tuned together so icon appears optically centered, not shifted.
- Touch target minimum 48dp remains mandatory.

### 5.3 Responsive behavior
- On compact widths (~320dp and up):
  - Navigation labels may truncate per existing behavior, but FAB must remain centered and fully tappable.
- On standard phones (360-411dp):
  - No overlap between FAB and nav labels/icons.
- On larger widths/tablets:
  - Center alignment remains geometrically centered relative to content viewport.

### 5.4 Visual acceptance criteria for alignment
- FAB center X minus viewport center X <= 1dp (implementation measurement tolerance).
- Gap center X minus FAB center X <= 1dp.
- No clipping in portrait or landscape.

## 6) Interaction States (All Three CR Items)
### 6.1 Common input states
- Default: standard outlined field.
- Focused: Material focused indicator clearly visible.
- Error: Material error color for supporting/error text and outline.
- Disabled (if ever used): maintain readable contrast.

### 6.2 Onboarding estimate states
- Loading/transient recompute: no spinner required; update inline.
- Valid: show numeric estimate + unit.
- Invalid/incomplete: placeholder helper text.
- Failure (domain validation exception): existing inline error block.

### 6.3 FAB states
- Resting: default FAB style.
- Pressed: Material pressed state/ripple.
- Expanded menu open: menu anchored to FAB, dismiss on outside tap/back.
- Disabled: not expected in this CR; if introduced later, reduce emphasis but keep icon legible.

## 7) Accessibility Requirements
- Screen reader:
  - Estimate block must be announced with semantic label and value (e.g., "Fabbisogno calorico stimato, 2350 kcal al giorno").
  - Macro fields must have labels without ambiguous double symbol wording.
  - FAB must retain clear content description for quick add action.
- Keyboard/IME:
  - Numeric keyboards for numeric fields.
  - Focus order follows visual order.
- Contrast and visibility:
  - All text/components meet at least WCAG AA contrast intent.
  - Focus indicators visible in both themes.

## 8) Motion and Transitions
- Keep motion subtle and consistent with Material defaults.
- Estimate value changes may use default content change behavior only; no custom animated counters.
- FAB menu opening uses existing dropdown animation timing.

## 9) Localization and Copy Notes
- Italian intent is primary in this CR.
- Suggested Italian strings:
  - Estimate label: "Fabbisogno calorico stimato"
  - Estimate placeholder: "Completa i dati profilo"
  - Unit: "kcal/giorno"
- Ensure English fallback remains coherent.
- Avoid mixing English labels in onboarding where Italian resources exist for same concept.

## 10) QA-Oriented Visual Acceptance Checklist
1. Onboarding shows estimate block before CTA when valid profile values are present.
2. Estimate persists/recovers correctly after app background/restore.
3. Macro fields never render duplicate `%` during typing, paste, focus change, save, or reload.
4. Macro labels/value presentation does not show `%%` confusion.
5. Center FAB appears aligned on 320dp, 360dp, 411dp, and landscape phone layouts.
6. FAB remains tappable and does not overlap bottom nav labels/icons in a way that blocks interaction.
7. Error messaging remains clear and unchanged in tone for invalid numeric/macro sum input.
8. Screen reader announces estimate and FAB action meaningfully.

## 11) Assets and Tokens
- No new icon library, font family, or design token required.
- Reuse Material 3 defaults and existing theme palette.
- Optional minor string resource additions/edits only for clarity and localization consistency.

## 12) Out of Scope Guardrails
- No restructure of navigation architecture.
- No change to calorie computation formula.
- No redesign of dashboard/history/meal flows beyond necessary consistency touchpoints.
